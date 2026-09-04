package com.inditex.similarproducts.application.exception;

/**
 * Indicates that a product-provider operation exceeded its configured time budget.
 */
public final class ProductProviderTimeoutException extends ProductProviderException {

    public ProductProviderTimeoutException(String operation, Throwable cause) {
        super("Product provider timed out while " + operation, cause);
    }
}
