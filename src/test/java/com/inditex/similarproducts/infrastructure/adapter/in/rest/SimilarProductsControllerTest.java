package com.inditex.similarproducts.infrastructure.adapter.in.rest;

import com.inditex.similarproducts.application.exception.ProductNotFoundException;
import com.inditex.similarproducts.application.exception.ProductProviderTimeoutException;
import com.inditex.similarproducts.application.exception.ProductProviderUnavailableException;
import com.inditex.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.inditex.similarproducts.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimilarProductsControllerTest {

    private GetSimilarProductsUseCase useCase;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        useCase = mock(GetSimilarProductsUseCase.class);
        client = WebTestClient.bindToController(new SimilarProductsController(useCase))
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void exposesTheExpectedHttpContract() {
        when(useCase.getSimilarProducts("1")).thenReturn(Mono.just(List.of(
                new Product("2", "Dress", new BigDecimal("19.99"), true),
                new Product("3", "Blazer", new BigDecimal("29.99"), false))));

        client.get()
                .uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[0].name").isEqualTo("Dress")
                .jsonPath("$[0].price").isEqualTo(19.99)
                .jsonPath("$[0].availability").isEqualTo(true)
                .jsonPath("$[1].id").isEqualTo("3");
    }

    @Test
    void returnsNotFoundWhenThePrimaryLookupDoesNotFindTheProduct() {
        when(useCase.getSimilarProducts("unknown"))
                .thenReturn(Mono.error(new ProductNotFoundException("unknown")));

        client.get().uri("/product/unknown/similar")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
    }

    @Test
    void returnsBadGatewayWhenTheProviderFails() {
        when(useCase.getSimilarProducts("1"))
                .thenReturn(Mono.error(new ProductProviderUnavailableException("HTTP 500")));

        client.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody().isEmpty();
    }

    @Test
    void returnsGatewayTimeoutWhenThePrimaryLookupTimesOut() {
        when(useCase.getSimilarProducts("1")).thenReturn(Mono.error(
                new ProductProviderTimeoutException("retrieving IDs", new TimeoutException())));

        client.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody().isEmpty();
    }
}
