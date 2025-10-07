package com.reliaquest.api

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class ApiApplicationTest extends Specification {
    def "context loads"() {
        expect:
            true
    }
}
