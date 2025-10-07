package com.reliaquest.api.service

import com.reliaquest.api.model.BackendDeleteEmployeeResponseDto
import com.reliaquest.api.model.BackendEmployeeResponseDto
import com.reliaquest.api.model.EmployeeResponseConverter
import com.reliaquest.api.model.NewEmployeeRequest
import spock.lang.Specification

class EmployeeServiceTest extends Specification {
    def backendEmployeeService = Mock(BackendEmployeeService)
    def converter = new EmployeeResponseConverter()
    def employeeService = new EmployeeService(backendEmployeeService, converter)

    def "GetAllEmployees properly handles response"() {
        given:
            def response = employeeData.collect {
                BackendEmployeeResponseDto.builder()
                        .id(it.id)
                        .name(it.name)
                        .salary(it.salary)
                        .age(it.age)
                        .title(it.title)
                        .email(it.email)
                        .build()
            }
            backendEmployeeService.getAllEmployees() >> response
        when:
            def allEmployees = employeeService.getAllEmployees()
        then:
            allEmployees.size() == employeeData.size()
            allEmployees.collect { [it.id, it.name, it.title, it.age, it.salary, it.email] } as Set ==
                    employeeData.collect { [it.id, it.name, it.title, it.age, it.salary, it.email] } as Set
        where:
            employeeData = [
                    [
                            id    : "1",
                            name  : "name1",
                            title : "title1",
                            age   : 1,
                            salary: 1,
                            email : "email1"
                    ],
                    [
                            id    : "2",
                            name  : "name2",
                            title : "title2",
                            age   : 2,
                            salary: 2,
                            email : "email2"
                    ]
            ]
    }

    def "GetAllEmployees throws exceptions from backend"() {
        given:
            backendEmployeeService.getAllEmployees() >> { throw exception.newInstance("test") }
        when:
            employeeService.getAllEmployees()
        then:
            thrown(RuntimeException)
        where:
            exception << [TooManyRequestsException, MalformedRequestException, NotFoundException, UnexpectedServerException]
    }

    def "FindEmployeeById returns properly converted response"() {
        given:
            backendEmployeeService.findEmployeeById(employee.id) >> Optional.of(employee)
        when:
            def employeeResponse = employeeService.getEmployeeById(employee.id)
        then:
            employeeResponse.id == employee.id
            employeeResponse.name == employee.name
            employeeResponse.title == employee.title
            employeeResponse.age == employee.age
            employeeResponse.salary == employee.salary
            employeeResponse.email == employee.email
        where:
            employee = BackendEmployeeResponseDto.builder()
                    .id(UUID.randomUUID().toString())
                    .name('Frank')
                    .title('title')
                    .age(1)
                    .salary(1)
                    .email('email')
                    .build()
    }

    def "FindEmployeeById returns null for missing employee"() {
        given:
            backendEmployeeService.findEmployeeById(id) >> Optional.empty()
        when:
            def employeeResponse = employeeService.getEmployeeById(id)
        then:
            employeeResponse == null
        where:
            id = UUID.randomUUID().toString()
    }

    def "CreateEmployee returns full employee response"() {
        backendEmployeeService.createEmployee(_) >> Optional.of(BackendEmployeeResponseDto.builder()
                .id(id)
                .name(name)
                .title(title)
                .age(age)
                .salary(salary)
                .email(email)
                .build())
        when:
            def employee = employeeService.createEmployee(new NewEmployeeRequest(name, salary, age, title))
        then:
            employee.id == id
            employee.name == name
            employee.title == title
            employee.age == age
            employee.salary == salary
            employee.email == email
        where:
            name = 'name'
            title = 'title'
            age = 1
            salary = 1
            email = 'email'
            id = UUID.randomUUID().toString()
    }

    def "DeleteEmployee property converts ID to name"() {
        given:
            backendEmployeeService.findEmployeeById(employee.id) >> Optional.of(employee)
        when:
            def deleted = employeeService.deleteEmployeeById(employee.id)
        then:
            1 * backendEmployeeService.deleteEmployee(employee.name) >> Optional.of(BackendDeleteEmployeeResponseDto.builder().data(true).build())
            deleted.name == employee.name
        where:
            employee = BackendEmployeeResponseDto.builder()
                    .id(UUID.randomUUID().toString())
                    .name('Frank')
                    .title('title')
                    .age(1)
                    .salary(1)
                    .email('email')
                    .build()

    }

    def "Failed DeleteEmployee returns null"() {
        given:
            backendEmployeeService.findEmployeeById(employee.id) >> Optional.of(employee)
        when:
            def deleted = employeeService.deleteEmployeeById(employee.id)
        then:
            1 * backendEmployeeService.deleteEmployee(employee.name) >> Optional.of(BackendDeleteEmployeeResponseDto.builder().data(false).build())
            deleted == null
        where:
            employee = BackendEmployeeResponseDto.builder()
                    .id(UUID.randomUUID().toString())
                    .name('Frank')
                    .title('title')
                    .age(1)
                    .salary(1)
                    .email('email')
                    .build()

    }


    def "DeleteEmployee on missing employee returns null"() {
        given:
            backendEmployeeService.findEmployeeById(employee.id) >> Optional.empty()
        when:
            def deleted = employeeService.deleteEmployeeById(employee.id)
        then:
            0 * backendEmployeeService.deleteEmployee(employee.name)
            deleted == null
        where:
            employee = BackendEmployeeResponseDto.builder()
                    .id(UUID.randomUUID().toString())
                    .name('Frank')
                    .title('title')
                    .age(1)
                    .salary(1)
                    .email('email')
                    .build()

    }


    def "finding employee by name properly does case insensitive search"() {
        given:
            def employee = BackendEmployeeResponseDto.builder()
                    .id(UUID.randomUUID().toString())
                    .name(name)
                    .title('title')
                    .age(1)
                    .salary(1)
                    .email('email')
                    .build()
            backendEmployeeService.getAllEmployees() >> [employee]
        when:
            def employees = employeeService.getEmployeesMatchingName('name')
        then:
            employees.size() == 1
            employees[0].name == name
        where:
            name << ["name", "Name", "NAME"]
    }


    def "finding employee by name properly matches substrings"() {
        given:
            backendEmployeeService.getAllEmployees() >> names.collect {
                BackendEmployeeResponseDto.builder()
                        .id(UUID.randomUUID().toString())
                        .name(it)
                        .salary(1000)
                        .age(50)
                        .title('employee')
                        .email("${it}@company.com")
                        .build()
            }
        when:
            def employees = employeeService.getEmployeesMatchingName('on')
        then:
            employees.size() == 3
            employees.find { 'No match here' == it.name } == null
        where:
            names = [
                    'Frank Jones',
                    'Bill One',
                    'Mona Lisa Smile',
                    'No match here'
            ]
    }
}
