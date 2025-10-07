package com.reliaquest.api.model

import spock.lang.Specification
import spock.lang.Subject

class EmployeeResponseConverterTest extends Specification {
    @Subject
    def converter = new EmployeeResponseConverter()

    def "all fields are mapped correctly"() {
        given:
            def source = new BackendEmployeeResponseDto(id, name, salary, age, title, email)
        when:
            def target = converter.convert(source)
        then:
            target.id == id
            target.name == name
            target.title == title
            target.age == age
            target.salary == salary
            target.email == email
        where:
            id = UUID.randomUUID().toString()
            name = "name"
            title = "title"
            age = 1
            salary = 1
            email = "email"
    }
}
