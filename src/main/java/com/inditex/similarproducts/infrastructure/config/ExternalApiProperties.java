package com.inditex.similarproducts.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Validated network settings for the existing product API.
 *
 * @param baseUrl absolute HTTP(S) base URL
 * @param connectTimeout maximum time allowed to establish a connection
 * @param responseTimeout maximum interval allowed while waiting for a response/read
 */
@Validated
@ConfigurationProperties("external-api")
public record ExternalApiProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout
) {

    public ExternalApiProperties {
        if (baseUrl != null && (!baseUrl.isAbsolute()
                || baseUrl.getHost() == null
                || !(baseUrl.getScheme().equalsIgnoreCase("http")
                || baseUrl.getScheme().equalsIgnoreCase("https")))) {
            throw new IllegalArgumentException("External API base URL must be an absolute HTTP URL");
        }
        requirePositive(connectTimeout, "Connection timeout");
        requirePositive(responseTimeout, "Response timeout");
    }

    private static void requirePositive(Duration duration, String propertyName) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(propertyName + " must be greater than zero");
        }
    }
}
