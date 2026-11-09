package com.nttdata.bank.account.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Maps account rule failures to HTTP responses.
 */
@RestControllerAdvice
public class AccountExceptionHandler {

    /**
     * @param ex business rule failure
     * @return HTTP 422
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> business(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorBody(ex.getMessage()));
    }

    /**
     * @param ex downstream outage
     * @return HTTP 503
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> unavailable(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorBody(ex.getMessage()));
    }

    /**
     * @param ex bean validation failure
     * @return HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorBody(message));
    }

    /**
     * @param message explanation safe to show to the caller
     */
    public record ErrorBody(String message) {
    }
}
