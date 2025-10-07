package com.reliaquest.api.controller;
import com.reliaquest.api.service.MalformedRequestException;
import com.reliaquest.api.service.NotFoundException;
import com.reliaquest.api.service.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@Slf4j
@ControllerAdvice
public class EmployeeControllerErrorHandler {
    @ExceptionHandler
    protected ResponseEntity<?> handleException(Throwable ex) {
        if(log.isDebugEnabled()) {
            // This could really be handled just fine in a custom logging configuration, but that starts to make
            // some assumptions about the overall logging strategy that seemed out of scope for this, so I kept it
            // simple by doing this here.
            log.warn("Error processing api request.", ex);
        } else {
            log.warn("Error processing api request: {}", ex.getMessage());
        }
        if (ex instanceof TooManyRequestsException) {
            return ResponseEntity.status(429).build();
        } else if (ex instanceof MalformedRequestException) {
            return ResponseEntity.badRequest().build();
        } else if (ex instanceof NotFoundException) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.internalServerError().build();
        }
    }
}
