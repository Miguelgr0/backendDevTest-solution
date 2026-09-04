package com.inditex.similarproducts.infrastructure.adapter.out.http;

import com.inditex.similarproducts.domain.model.Product;

import java.math.BigDecimal;

/**
 * Transport representation of the downstream JSON payload. Keeping it separate prevents the
 * external API schema from becoming the domain model by accident.
 */
record ProductApiResponse(String id, String name, BigDecimal price, Boolean availability) {

    Product toDomain() {
        if (availability == null) {
            throw new IllegalArgumentException("Product availability must not be null");
        }
        return new Product(id, name, price, availability);
    }
}
