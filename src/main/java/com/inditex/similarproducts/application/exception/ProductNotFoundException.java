package com.inditex.similarproducts.application.exception;

/**
 * Indicates that the product provider has no resource for the requested identifier.
 */
public final class ProductNotFoundException extends ProductProviderException {

    public ProductNotFoundException(String productId) {
        super("Product " + productId + " was not found by the product provider");
    }
}
