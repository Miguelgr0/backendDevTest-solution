package com.inditex.similarproducts.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Use-case tuning parameters.
 *
 * @param maxConcurrency maximum number of detail publishers subscribed at once per request
 */
@Validated
@ConfigurationProperties("similar-products")
public record SimilarProductsProperties(@Min(1) int maxConcurrency) {
}
