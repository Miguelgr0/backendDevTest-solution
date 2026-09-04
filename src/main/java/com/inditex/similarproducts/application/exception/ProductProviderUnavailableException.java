package com.inditex.similarproducts.application.exception;

/**
 * Represents connection failures, downstream server errors, and unusable provider responses.
 */
public final class ProductProviderUnavailableException extends ProductProviderException {

    public ProductProviderUnavailableException(String message) {
        super(message);
    }

    public ProductProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
