package com.inditex.similarproducts.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable product representation used by the core application.
 *
 * @param id unique product identifier
 * @param name customer-facing product name
 * @param price product price as an exact decimal value
 * @param availability whether the product is currently available
 */
public record Product(String id, String name, BigDecimal price, boolean availability) {

    public Product {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        Objects.requireNonNull(price, "Product price must not be null");
    }
}
