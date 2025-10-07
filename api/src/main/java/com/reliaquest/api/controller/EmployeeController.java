package com.reliaquest.api.controller;

import com.reliaquest.api.model.EmployeeResponse;
import com.reliaquest.api.model.NewEmployeeRequest;
import com.reliaquest.api.service.EmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
public class EmployeeController implements IEmployeeController<EmployeeResponse, NewEmployeeRequest> {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Override
    public ResponseEntity<List<EmployeeResponse>> getAllEmployees() {
        log.debug("Received request for getAllEmployees()");
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @Override
    public ResponseEntity<List<EmployeeResponse>> getEmployeesByNameSearch(String searchString) {
        log.debug("Received request for getEmployeesByNameSearch({})", searchString);
        return ResponseEntity.ok(employeeService.getEmployeesMatchingName(searchString));
    }

    @Override
    public ResponseEntity<EmployeeResponse> getEmployeeById(String id) {
        log.debug("Received request for getEmployeeById({})", id);
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    @Override
    public ResponseEntity<Integer> getHighestSalaryOfEmployees() {
        log.debug("Received request for getHighestSalaryOfEmployees()");
        return ResponseEntity.ok(employeeService.getTopPaidEmployees(1).get(0).getSalary());
    }

    @Override
    public ResponseEntity<List<String>> getTopTenHighestEarningEmployeeNames() {
        log.debug("Received request for getTopTenHighestEarningEmployeeNames()");
        return ResponseEntity.ok(employeeService.getTopPaidEmployees(10).stream()
                .map(EmployeeResponse::getName)
                .toList());
    }

    @Override
    public ResponseEntity<EmployeeResponse> createEmployee(NewEmployeeRequest employeeInput) {
        log.debug("Received request for createEmployee({})", employeeInput.getName());
        return ResponseEntity.ok(employeeService.createEmployee(employeeInput));
    }

    @Override
    public ResponseEntity<String> deleteEmployeeById(String id) {
        log.debug("Received request for deleteEmployeeById({})", id);
        EmployeeResponse employeeResponse = employeeService.deleteEmployeeById(id);
        if(employeeResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(employeeResponse.getName());
    }
}
