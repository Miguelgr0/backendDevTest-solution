package com.inditex.similarproducts.application.exception;

/**
 * Base exception for failures reported through the product-provider boundary.
 */
public class ProductProviderException extends RuntimeException {

    public ProductProviderException(String message) {
        super(message);
    }

    public ProductProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
