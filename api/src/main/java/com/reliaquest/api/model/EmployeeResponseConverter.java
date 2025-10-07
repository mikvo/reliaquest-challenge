package com.reliaquest.api.model;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class EmployeeResponseConverter implements Converter<BackendEmployeeResponseDto, EmployeeResponse> {
    @Override
    public EmployeeResponse convert(BackendEmployeeResponseDto source) {
        return EmployeeResponse.builder()
                .id(source.getId())
                .title(source.getTitle())
                .name(source.getName())
                .age(source.getAge())
                .email(source.getEmail())
                .salary(source.getSalary())
                .build();
    }
}
