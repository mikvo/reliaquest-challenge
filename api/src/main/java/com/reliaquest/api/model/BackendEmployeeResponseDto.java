package com.reliaquest.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BackendEmployeeResponseDto {
    private String id;
    @JsonProperty("employee_name")
    private String name;
    @JsonProperty("employee_salary")
    private Integer salary;
    @JsonProperty("employee_age")
    private Integer age;
    @JsonProperty("employee_title")
    private String title;
    @JsonProperty("employee_email")
    private String email;
}
