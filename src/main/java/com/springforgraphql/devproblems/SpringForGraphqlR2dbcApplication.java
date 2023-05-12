package com.springforgraphql.devproblems;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Dev Problems(A Sarang Kumar Tak)
 * @YoutubeChannel https://www.youtube.com/channel/UCVno4tMHEXietE3aUTodaZQ
 */
@SpringBootApplication
@Configuration
public class SpringForGraphqlR2dbcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringForGraphqlR2dbcApplication.class, args);
    }

	@Bean
	public HttpGraphQlClient httpGraphQlClient() {
		return HttpGraphQlClient.builder().url("http://localhost:8080/graphql").build();
	}
}

@RestController
@Slf4j
class GraphQlController {

	private final EmployeeRepository employeeRepository;
	private final DepartmentRepository departmentRepository;

	private final HttpGraphQlClient httpGraphQlClient;

	public GraphQlController(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository, HttpGraphQlClient httpGraphQlClient) {
		this.employeeRepository = employeeRepository;
		this.departmentRepository = departmentRepository;
		this.httpGraphQlClient = httpGraphQlClient;
	}

	Function<AddEmployeeInput, Employee> mapping = aei -> {
		var employee = new Employee();
		employee.setName(aei.getName());
		employee.setSalary(aei.getSalary());
		employee.setDepartmentId(aei.getDepartmentId());
		return employee;
	};

	@GetMapping("/employeeByName")
	public Mono<List<Employee>> employeeByName() {
		var document = "query {\n" +
				"  employeeByName(employeeName: \"devproblems\") {\n" +
				"    id, name, salary\n" +
				"  }\n" +
				"}";
		return this.httpGraphQlClient.document(document)
				.retrieve("employeeByName")
				.toEntityList(Employee.class);
	}

	@MutationMapping
	public Mono<Employee> addEmployee(@Argument AddEmployeeInput addEmployeeInput) {
		return this.employeeRepository.save(mapping.apply(addEmployeeInput));
	}

	@QueryMapping
	public Flux<Employee> employeeByName(@Argument String employeeName) {
		return this.employeeRepository.getEmployeeByName(employeeName);
	}

	@MutationMapping
	public Mono<Employee> updateSalary(@Argument UpdateSalaryInput updateSalaryInput) {
		return this.employeeRepository.findById(updateSalaryInput.getEmployeeId())
				.flatMap(employee -> {
					employee.setSalary(updateSalaryInput.getSalary());
					return this.employeeRepository.save(employee);
				});
	}

	@QueryMapping
	public Flux<Department> allDepartment() {
		return this.departmentRepository.findAll();
	}

	@BatchMapping
	public Mono<Map<Department, Collection<Employee>>> employees(List<Department> departments) {
		return Flux.fromIterable(departments)
				.flatMap(department -> this.employeeRepository.getAllEmployeeByDepartmentId(department.getId()))
				.collectMultimap(employee -> departments.stream().filter(department -> department.getId().equals(employee.getDepartmentId())).findFirst().get());
	}

	@SubscriptionMapping
	public Flux<Employee> allEmployee() {
		return this.employeeRepository.findAll().delayElements(Duration.ofSeconds(3));
	}

}


@Data
@NoArgsConstructor
@AllArgsConstructor
class Employee {

    @Id
    private Integer id;
    private String name, salary;
	@Column("department_id")
    private Integer departmentId;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Department {
    @Id
    private Integer id;
    private String name;
    private List<Employee> employees = new ArrayList<>();
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class AddEmployeeInput {
    private String name, salary;
    private Integer departmentId;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
class UpdateSalaryInput {
    private Integer employeeId;
    private String salary;
}


interface EmployeeRepository extends ReactiveCrudRepository<Employee, Integer> {
	Flux<Employee> getEmployeeByName(String name);
	Flux<Employee> getAllEmployeeByDepartmentId(Integer departmentId);
}
interface DepartmentRepository extends ReactiveCrudRepository<Department, Integer> {}

@Component
@Slf4j
class GraphQlServerInterceptor implements WebGraphQlInterceptor {

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		log.info("interceptor logs {}", request.getDocument());
		return chain.next(request);
	}
}