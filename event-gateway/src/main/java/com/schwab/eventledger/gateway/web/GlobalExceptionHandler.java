package com.schwab.eventledger.gateway.web;

import com.schwab.eventledger.gateway.dto.ErrorResponse;
import com.schwab.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.schwab.eventledger.gateway.exception.EventNotFoundException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing, malformed, or contains an invalid field value");
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EventNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(AccountServiceUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_SERVICE_UNAVAILABLE", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        String traceId = MDC.get("traceId");
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, traceId));
    }
}
