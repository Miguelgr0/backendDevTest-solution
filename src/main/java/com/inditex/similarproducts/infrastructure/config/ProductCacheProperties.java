package com.inditex.similarproducts.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Bounds and freshness policy for the local product-detail cache.
 *
 * @param maximumSize maximum number of successful product entries
 * @param ttl duration for which a successful detail remains reusable
 */
@Validated
@ConfigurationProperties("cache.products")
public record ProductCacheProperties(
        @Min(1) long maximumSize,
        @NotNull Duration ttl
) {

    public ProductCacheProperties {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("Product cache TTL must be greater than zero");
        }
    }
}
