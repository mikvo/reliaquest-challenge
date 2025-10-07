package com.reliaquest.api.service;

import com.reliaquest.api.config.BackendServiceConfig;
import com.reliaquest.api.model.BackendDeleteEmployeeDto;
import com.reliaquest.api.model.BackendDeleteEmployeeResponseDto;
import com.reliaquest.api.model.BackendEmployeeDto;
import com.reliaquest.api.model.BackendEmployeeListDto;
import com.reliaquest.api.model.BackendEmployeeResponseDto;
import com.reliaquest.api.model.NewEmployeeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/**
 * This class is responsible for all communication with the backend employee service.  It is separated from the
 * EmployeeService to allow for easier testing and mocking but also to facilitate the rudimentary caching mechanism
 * that is in place. This is not a production-worthy implementation as using a more robust cache provider would be
 * required that supports sharding and expiration -- ideally a distributee cache like Redis or Hazelcast. This is
 * really here just to highlight the concept that caching can help with a service that is as course-grained as the
 * employee service is. Note that it doesn't really deal with the notion that restarting the backend service will
 * re-generate the data. This means that the api module would also have to be restarted any time the backend is
 * restarted. A normal production service wouldn't have that regeneration side effect, so this serves the purpose of
 * illustrating the concept.
 * <p>
 * Also, while this service does handle retry logic for 429 responses, the caching implementation serves to reduce
 * the impact of the rate limiting. That doesn't help with mutating operations (create, delete), however, and these
 * are retried until successful (or retries are exhausted).
 */
@Slf4j
@Service
public class BackendEmployeeService {
    private final WebClient webClient;
    private final BackendServiceConfig config;
    private BackendEmployeeListDto employeeCache;

    public BackendEmployeeService(WebClient.Builder builder, BackendServiceConfig config) {
        this.config = config;
        this.webClient = builder.baseUrl(config.getUrl()).build();
    }

    /**
     * Represents the back-end service call to retrieve all employees.
     *
     * @return A list of all employees from the backend.
     */
    public List<BackendEmployeeResponseDto> getAllEmployees() {
        Optional<BackendEmployeeListDto> response = performRequest("", BackendEmployeeListDto.class, () -> employeeCache);
        response.ifPresent(list -> {
            employeeCache = list;
            log.info("Updated employee cache with {} employees.", list.getData().size());
        });
        return response.map(BackendEmployeeListDto::getData)
                .orElse(List.of());
    }

    public Optional<BackendEmployeeResponseDto> findEmployeeById(String id) {
        return performRequest("/"+id, BackendEmployeeDto.class, () -> cachedEmployeeWithId(id))
                .map(BackendEmployeeDto::getData);
    }

    private BackendEmployeeDto cachedEmployeeWithId(String id) {
        log.debug("Checking cache for employee with id={}.", id);
        return employeeCache.getData().stream()
                .filter(employee -> employee.getId().equals(id))
                .findFirst()
                .map(dto -> BackendEmployeeDto.builder().data(dto).build())
                .orElse(null);
    }

    public Optional<BackendEmployeeResponseDto> createEmployee(NewEmployeeRequest employee) {
        employeeCache = null; // clear cache on any mutating operation
        return performRequestWithBody(HttpMethod.POST, "", employee, BackendEmployeeDto.class)
                .map(BackendEmployeeDto::getData);
    }

    public Optional<BackendDeleteEmployeeResponseDto> deleteEmployee(String name) {
        employeeCache = null; // clear cache on any mutating operation
        BackendDeleteEmployeeDto deleteRequest = BackendDeleteEmployeeDto.builder().name(name).build();
        return performRequestWithBody(HttpMethod.DELETE, "", deleteRequest, BackendDeleteEmployeeResponseDto.class);
    }

    /**
     * Make a request to the backend that includes a body. The @CacheEvict is here because we know at this point that
     * all calls to this method are mutations to the state of the employee database -- create or delete.
     *
     * @param method        The request type (POST, DELETE, etc.)
     * @param uri           The URI to request, relative to the base URL. To use only the base URL, pass an empty string.
     * @param body          The body of the request
     * @param responseClazz The class type of the response
     * @return An optional containing the response, or an empty optional if the request failed
     */
    private <T> Optional<T> performRequestWithBody(HttpMethod method, String uri, Object body, Class<T> responseClazz) {
        return webClient.method(method)
                .uri(uri)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseClazz)
                .retryWhen(buildRetrySpec())
                .onErrorMap(this::translateException)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .doOnSuccess( opt -> opt.ifPresent(result -> log.debug("{} request completed with response: {}.", method, result)))
                .block();
    }


    /**
     * Make a GET request to the backend without a body.
     *
     * @param uri           The URI to request, relative to the base URL. To use only the base URL, pass an empty string.
     * @param responseClazz The class type of the response
     * @return An optional containing the response, or an empty optional if the request failed
     */
    private <T> Optional<T> performRequest(String uri, Class<T> responseClazz, Supplier<T> emptySupplier) {
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(responseClazz)
                .onErrorResume(WebClientResponseException.TooManyRequests.class,
                        e -> Mono.justOrEmpty(emptySupplier.get())
                                .doOnNext(v -> log.info("Rate limited, returning cached response for {}.", uri))
                                .switchIfEmpty(Mono.error(e)))
                .retryWhen(buildRetrySpec())
                .onErrorMap(this::translateException)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .doOnSuccess( opt -> opt.ifPresent(result -> log.debug("GET request completed with response: {}.", result)))
                .block();
    }


    /**
     * Build the retry specification for the WebClient. This same spec will be used by all back-end calls.
     *
     * @return The retry specification
     */
    private RetryBackoffSpec buildRetrySpec() {
        return Retry.backoff(config.getMaxRetries(), config.getRetryBackoff())
                .maxBackoff(config.getMaxBackoff())
                .filter(this::shouldRetry)
                .doBeforeRetry(this::logBeforeRetry);
    }


    /**
     * Translate exceptions from the backend into more meaningful exceptions. This is primarily to prevent callers of
     * this service from having to know the details of the backend implementation. Java 21 would make this method
     * cleaner with pattern matching on the throwable. Kotlin would be even cleaner.
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
     *
     * @param retryContext The retry context from the WebClient reactor.
     */
    private void logBeforeRetry(Retry.RetrySignal retryContext) {
        log.info("Retrying due to rate limiting [attempt={}]...", retryContext.totalRetriesInARow()+1);
    }


    /**
     * A filter used by WebClient to determine whether a retry should be attempted. For this implementation, we only
     * retry on 429 responses.
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
