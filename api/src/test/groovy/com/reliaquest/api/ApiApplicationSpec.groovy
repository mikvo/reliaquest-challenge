package com.reliaquest.api

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class ApiApplicationSpec extends Specification {
    def "context loads"() {
        expect:
            true
    }
}
