package com.reliaquest.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponse {
    private String id;
    private String name;
    private Integer salary;
    private Integer age;
    private String title;
    private String email;
}
