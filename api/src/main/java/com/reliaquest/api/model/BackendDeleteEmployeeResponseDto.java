package com.reliaquest.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BackendDeleteEmployeeResponseDto {
    private boolean data;
    private String status;
    private String error;
}
