package com.reliaquest.api.service;

import com.reliaquest.api.model.BackendDeleteEmployeeResponseDto;
import com.reliaquest.api.model.BackendEmployeeResponseDto;
import com.reliaquest.api.model.EmployeeResponse;
import com.reliaquest.api.model.NewEmployeeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmployeeService {
    private final BackendEmployeeService backendEmployeeService;
    private final Converter<BackendEmployeeResponseDto, EmployeeResponse> converter;

    public EmployeeService(BackendEmployeeService backendEmployeeService, Converter<BackendEmployeeResponseDto, EmployeeResponse> converter) {
        this.backendEmployeeService = backendEmployeeService;
        this.converter = converter;
    }

    /**
     * Retrieve all employees from the backend. This assumes that the backend always returns all employees as there is
     * no pagination or other means of limiting the number of employees returned.
     *
     * @return A list of EmployeeResponse objects, one for each employee in the backend.
     */
    public List<EmployeeResponse> getAllEmployees() {
        log.debug("Calling getAllEmployees()");
        return getAllEmployeesFromBackend().stream()
                .map(converter::convert)
                .toList();
    }


    /**
     * Retrieve a list of employees from the backend that include the given name fragment. The names are compared using
     * a case-insensitive comparison.
     *
     * @param nameFragment The name fragment to search for.
     * @return A list of employees that match the name fragment, or an empty list if no employees match.
     */
    public List<EmployeeResponse> getEmployeesMatchingName(String nameFragment) {
        log.debug("Calling getEmployeesMatchingName({})", nameFragment);
        List<EmployeeResponse> response = getAllEmployeesFromBackend().stream()
                .filter(employee -> employee.getName().toLowerCase().contains(nameFragment.toLowerCase()))
                .map(converter::convert)
                .toList();
        log.info("Retrieved {} employees matching name fragment {}.", response.size(), nameFragment);
        return response;
    }


    /**
     * Get the top paid employees from the backend. Note that this does not allow for expanding the collection if the
     * employee in the last position has the same salary as other employees. In this case, the list may be partially
     * misleading.
     *
     * @param count The number of employees to return
     * @return a list of up to {@code count} employees
     */
    public List<EmployeeResponse> getTopPaidEmployees(int count) {
        log.debug("Calling getTopPaidEmployees({})", count);
        return getAllEmployeesFromBackend().stream()
                .sorted((o1, o2) -> o2.getSalary().compareTo(o1.getSalary()))
                .limit(count)
                .map(converter::convert)
                .toList();
    }

    /**
     * Delete an employee by id.
     * NOTE: This method is buggy because the back-end requires a name to delete, not an id and if multiple employees
     * have the same name, it will delete the first one it finds.  This is a bug in the back-end API and not something
     * the front-end can easily fix.
     *
     * @param id The ID of the employee to delete
     * @return The employee that was deleted (or, at least, the employee that matched the ID of the delete request)
     */
    public EmployeeResponse deleteEmployeeById(String id) {
        log.debug("Calling deleteEmployeeById({})", id);
        // This call with throw a NotFoundException if the employee does not exist
        EmployeeResponse employeeToDelete = getEmployeeById(id);
        if(employeeToDelete != null) {
            boolean deleteSuccessful = backendEmployeeService.deleteEmployee(employeeToDelete.getName())
                    .map(BackendDeleteEmployeeResponseDto::isData)
                    .orElse(false);
            if(deleteSuccessful) {
                log.info("Deleted employee with name={} from backend.", employeeToDelete.getName());
                return employeeToDelete;
            }
            log.warn("Failed to delete employee with name={} (id={}).", employeeToDelete.getName(), id);
        } else {
            log.warn("Failed to find employee to delete for id={}.", id);
        }

        return null;
    }


    public EmployeeResponse getEmployeeById(String id) {
        log.debug("Calling getEmployeeFromBackendById({})", id);
        return backendEmployeeService.findEmployeeById(id)
                .map(converter::convert)
                .orElse(null);
    }

    public EmployeeResponse createEmployee(NewEmployeeRequest employee) {
        log.debug("Calling createEmployee({})", employee);
        return backendEmployeeService.createEmployee(employee)
                .map(converter::convert)
                .orElse(null);
    }

    /**
     * A convenience method to retrieve all employees from the backend.
     *
     * @return A list of all employees from the backend.
     */
    private List<BackendEmployeeResponseDto> getAllEmployeesFromBackend() {
        List<BackendEmployeeResponseDto> allEmployees = backendEmployeeService.getAllEmployees();
        log.info("Retrieved {} employees from backend.", allEmployees.size());
        return allEmployees;
    }
}
