package com.reliaquest.api.service;

import com.reliaquest.api.config.BackendServiceConfig;
import com.reliaquest.api.model.BackendDeleteEmployeeDto;
import com.reliaquest.api.model.BackendDeleteEmployeeResponseDto;
import com.reliaquest.api.model.BackendEmployeeDto;
import com.reliaquest.api.model.BackendEmployeeListDto;
import com.reliaquest.api.model.BackendEmployeeResponseDto;
import com.reliaquest.api.model.EmployeeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Slf4j
@Service
public class BackendEmployeeService {
    private final WebClient webClient;
    private final Converter<BackendEmployeeResponseDto, EmployeeResponse> converter;
    private final BackendServiceConfig config;

    public BackendEmployeeService(WebClient.Builder builder, Converter<BackendEmployeeResponseDto, EmployeeResponse> converter, BackendServiceConfig config) {
        this.converter = converter;
        this.config = config;
        this.webClient = builder.baseUrl(config.getUrl()).build();
    }

    /**
     * Retrieve all employees from the backend. This assumes that the backend always returns all employees as there is
     * no pagination or other means of limiting the number of employees returned. The caching here is super simple just
     * to show the concept. Frequently calling a service to retrieve all employees is not efficient and while neither is
     * caching all employees, this illustrates that there are some things that can be done to mitigate. In this case
     * the cache is only cleared when an employee is deleted or inserted as the back-end is relatively static. It's not
     * a production-worthy implementation as using a more robust cache provider would be required.
     *
     * @return A list of EmployeeResponse objects, one for each employee in the backend.
     */
    @Cacheable(value = "all-employees")
    public List<EmployeeResponse> getAllEmployees() {
        List<EmployeeResponse> response =  getAllEmployeesFromBackend().stream()
                .map(converter::convert)
                .toList();
        return response;
    }


    /**
     * Retrieve a list of employees from the backend that include the given name fragment. The names are compared using
     * a case-insensitive comparison.
     *
     * @param nameFragment The name fragment to search for.
     * @return A list of employees that match the name fragment, or an empty list if no employees match.
     */
    public List<EmployeeResponse> getEmployeesMatchingName(String nameFragment) {
        List<EmployeeResponse> response = getAllEmployeesFromBackend().stream()
                .filter(employee -> employee.getName().toLowerCase().contains(nameFragment.toLowerCase()))
                .map(converter::convert)
                .toList();
        log.info("Retrieved {} employees matching name fragment {} from backend.", response.size(), nameFragment);
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
    @CacheEvict("all-employees")
    public EmployeeResponse deleteEmployeeById(String id) {
        log.info("Deleting employee with id={} from backend.", id);
        // This call with throw a NotFoundException if the employee does not exist
        EmployeeResponse employeeToDelete = getEmployeeFromBackendById(id);
        if(employeeToDelete != null) {
            BackendDeleteEmployeeDto deleteRequest = BackendDeleteEmployeeDto.builder().name(employeeToDelete.getName()).build();
            if(performBackendRequestWithBody(HttpMethod.DELETE, "", deleteRequest, BackendDeleteEmployeeResponseDto.class)
                    .map(BackendDeleteEmployeeResponseDto::isData)
                    .orElse(false)) {
                log.info("Deleted employee with name={} and id={} from backend.", employeeToDelete.getName(), employeeToDelete.getId());
                return employeeToDelete;
            }
        }

        return null;
    }

    /**
     * Make a request to the backend that includes a body.
     *
     * @param method        The request type (POST, DELETE, etc.)
     * @param uri           The URI to request, relative to the base URL. To use only the base URL, pass an empty string.
     * @param body          The body of the request
     * @param responseClazz The class type of the response
     * @return An optional containing the response, or an empty optional if the request failed
     */
    private <T> Optional<T> performBackendRequestWithBody(HttpMethod method, String uri, Object body, Class<T> responseClazz) {
        return webClient.method(method)
                .uri(uri)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseClazz)
                .retryWhen(Retry.backoff(config.getMaxRetries(), config.getRetryBackoff())
                        .maxBackoff(config.getMaxBackoff())
                        .filter(this::shouldRetry)
                        .doBeforeRetry(this::logBeforeRetry))
                .onErrorMap(this::translateException)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .block();
    }


    /**
     * Make a GET request to the backend without a body.
     *
     * @param uri           The URI to request, relative to the base URL. To use only the base URL, pass an empty string.
     * @param responseClazz The class type of the response
     * @return An optional containing the response, or an empty optional if the request failed
     */
    private <T> Optional<T> performBackendRequest(String uri, Class<T> responseClazz) {
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(responseClazz)
                .retryWhen(Retry.backoff(config.getMaxRetries(), config.getRetryBackoff())
                        .maxBackoff(config.getMaxBackoff())
                        .filter(this::shouldRetry)
                        .doBeforeRetry(this::logBeforeRetry))
                .onErrorMap(this::translateException)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .block();
    }


    public EmployeeResponse getEmployeeFromBackendById(String id) {
        log.info("Retrieving employee {} from backend.", id);
        return performBackendRequest("/"+id, BackendEmployeeDto.class)
                .map(BackendEmployeeDto::getData)
                .map(converter::convert)
                .orElse(null);
    }

    /**
     * A convenience method to retrieve all employees from the backend.
     *
     * @return A list of all employees from the backend.
     */
    private List<BackendEmployeeResponseDto> getAllEmployeesFromBackend() {
        log.info("Retrieving all employees from backend.");
        List<BackendEmployeeResponseDto> allEmployees = performBackendRequest("", BackendEmployeeListDto.class)
                .map(BackendEmployeeListDto::getData)
                .orElse(List.of());
        log.info("Retrieved {} employees from backend.", allEmployees.size());
        return allEmployees;
    }

    /**
     * Translate exceptions from the backend into more meaningful exceptions. This is primarily to prevent callers of
     * this service from having to know the details of the backend implementation.
     *
     * @param throwable The original exception thrown by the WebClient call.
     * @return The translated exception.
     */
    private Throwable translateException(Throwable throwable) {
        if(Exceptions.isRetryExhausted(throwable)) {
            return new TooManyRequestsException("Retries exhausted on rate-limited employee service.", throwable.getCause());
        }
        if(throwable instanceof WebClientResponseException) {
            if(((WebClientResponseException) throwable).getStatusCode() == NOT_FOUND) {
                return new NotFoundException(throwable.getMessage(), throwable);
            }
            return new MalformedRequestException(throwable.getMessage(), throwable);
        }
        return new UnexpectedServerException(throwable.getMessage(), throwable);
    }

    /**
     * A utility method to log retry attempts so that the log can show the retry number.
     * @param retryContext
     */
    private void logBeforeRetry(Retry.RetrySignal retryContext) {
        log.info("Retrying due to rate limiting [attempt={}]...", retryContext.totalRetriesInARow()+1);
    }


    /**
     * A filter used by WebClient to determine whether a retry should be attempted.
     *
     * @param throwable The exception that caused the retry to be considered.
     * @return true if the retry should be attempted, false otherwise.
     */
    private boolean shouldRetry(Throwable throwable) {
        if ( throwable instanceof WebClientResponseException) {
            return ((WebClientResponseException) throwable).getStatusCode() == TOO_MANY_REQUESTS;
        }
        return false;
    }
}
