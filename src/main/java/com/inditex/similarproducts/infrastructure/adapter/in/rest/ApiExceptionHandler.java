package com.inditex.similarproducts.infrastructure.adapter.in.rest;

import com.inditex.similarproducts.application.exception.ProductNotFoundException;
import com.inditex.similarproducts.application.exception.ProductProviderTimeoutException;
import com.inditex.similarproducts.application.exception.ProductProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates failures of the mandatory similar-ID lookup into stable gateway-facing HTTP statuses.
 * Detail-level failures never reach this adapter because the use case handles them as partial data.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<Void> handleNotFound(ProductNotFoundException exception) {
        log.debug("Product provider reported a missing resource: {}", exception.getMessage());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ProductProviderTimeoutException.class)
    ResponseEntity<Void> handleTimeout(ProductProviderTimeoutException exception) {
        log.warn("Product provider timeout: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
    }

    @ExceptionHandler(ProductProviderUnavailableException.class)
    ResponseEntity<Void> handleUnavailable(ProductProviderUnavailableException exception) {
        log.warn("Product provider unavailable: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
