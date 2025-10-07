package com.reliaquest.api

import com.reliaquest.api.service.EmployeeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.Specification

import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployeeControllerIntegrationSpec extends Specification {

    @LocalServerPort
    int port

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    EmployeeService employeeService

    def initialEmployeeList

    def setup() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(150)).build()
        initialEmployeeList = employeeService.getAllEmployees()
        // This is a somewhat simplified way to ensure that we force the rate limiter, which will ensure that
        // subsequent create/delete calls will fail if the retry mechanism doesn't work. It's not a super
        // robust way to do it but works for the current mock API server and illustrates the point.
        for (int i = 0; i < 20; i++) {
            employeeService.getAllEmployees()
        }
    }

    def "GET returns list of employees"() {
        when:
            def response = webTestClient.get()
                    .uri("http://localhost:${port}")
                    .exchange()

        then:
            response.expectStatus().isOk()
                    .expectHeader().contentType("application/json")
                    .expectBody()
                    .jsonPath('$').isArray()
    }

    def "Creating then deleting an employee works"() {
        given:
            def newEmployee = [
                    name: "Unlikely Empl0yee Name",
                    age: 30,
                    salary: 75000,
                    title: "Developer",
            ]
        when: 'we create a new employee'
            def createdId = null
            def response = webTestClient.post()
                    .uri("http://localhost:${port}")
                    .bodyValue(newEmployee)
                    .exchange()

        then: 'we get a 200 and an ID back'
            response.expectStatus().isOk()
                    .expectBody()
                    .jsonPath('$.id').value(id -> createdId = id )

        when: 'we get all employees'
            def newEmployeeList = employeeService.getAllEmployees()

        then: 'we have one more employee'
            newEmployeeList.size() == initialEmployeeList.size() + 1

        when: 'we delete the new employee'
            def deleteResponse = webTestClient.delete()
                    .uri("http://localhost:${port}/$createdId")
                    .exchange()
        then: 'we get a 200 and the name back'
            deleteResponse.expectStatus().isOk()
                    .expectBody()
                    .jsonPath('$').isEqualTo(newEmployee.name)

        when: 'we get all employees again'
            newEmployeeList = employeeService.getAllEmployees()
        then: 'we are back to original size'
            newEmployeeList.size() == initialEmployeeList.size()
    }
}
