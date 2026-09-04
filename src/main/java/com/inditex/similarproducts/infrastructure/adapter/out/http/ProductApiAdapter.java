package com.inditex.similarproducts.infrastructure.adapter.out.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.inditex.similarproducts.application.exception.ProductNotFoundException;
import com.inditex.similarproducts.application.exception.ProductProviderException;
import com.inditex.similarproducts.application.exception.ProductProviderTimeoutException;
import com.inditex.similarproducts.application.exception.ProductProviderUnavailableException;
import com.inditex.similarproducts.application.port.out.ProductProviderPort;
import com.inditex.similarproducts.domain.model.Product;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * WebClient implementation of the product-provider port. It owns downstream paths, JSON mapping,
 * status classification, and conversion of technical failures into provider-neutral exceptions.
 */
public final class ProductApiAdapter implements ProductProviderPort {

    private static final ParameterizedTypeReference<List<JsonNode>> ID_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public ProductApiAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<List<String>> getSimilarProductIds(String productId) {
        String operation = "retrieving similar IDs for product " + productId;
        return webClient.get()
                .uri("/product/{productId}/similarids", productId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> response.createException().map(ignored -> new ProductNotFoundException(productId)))
                .onStatus(HttpStatusCode::isError,
                        response -> response.createException().map(error -> new ProductProviderUnavailableException(
                                "Product provider returned HTTP " + error.getStatusCode().value() + " while " + operation)))
                .bodyToMono(ID_LIST_TYPE)
                .switchIfEmpty(Mono.error(new ProductProviderUnavailableException(
                        "Product provider returned an empty response while " + operation)))
                // The published OpenAPI declares strings, while the supplied mock emits JSON numbers.
                .map(ids -> ids.stream()
                        .map(id -> toProductId(id, operation))
                        .toList())
                .onErrorMap(error -> !(error instanceof ProductProviderException),
                        error -> mapUnexpectedError(operation, error));
    }

    @Override
    public Mono<Product> getProduct(String productId) {
        String operation = "retrieving product " + productId;
        return webClient.get()
                .uri("/product/{productId}", productId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> response.createException().map(ignored -> new ProductNotFoundException(productId)))
                .onStatus(HttpStatusCode::isError,
                        response -> response.createException().map(error -> new ProductProviderUnavailableException(
                                "Product provider returned HTTP " + error.getStatusCode().value() + " while " + operation)))
                .bodyToMono(ProductApiResponse.class)
                .switchIfEmpty(Mono.error(new ProductProviderUnavailableException(
                        "Product provider returned an empty response while " + operation)))
                .map(ProductApiResponse::toDomain)
                .onErrorMap(error -> !(error instanceof ProductProviderException),
                        error -> mapUnexpectedError(operation, error));
    }

    private ProductProviderException mapUnexpectedError(String operation, Throwable error) {
        Throwable unwrapped = Exceptions.unwrap(error);
        // Reactor Netty wraps different timeout implementations depending on the failing phase;
        // inspecting the cause chain keeps the application-facing classification consistent.
        if (isTimeout(unwrapped)) {
            return new ProductProviderTimeoutException(operation, unwrapped);
        }
        if (unwrapped instanceof WebClientRequestException requestException && isTimeout(requestException.getCause())) {
            return new ProductProviderTimeoutException(operation, requestException);
        }
        return new ProductProviderUnavailableException("Product provider failed while " + operation, unwrapped);
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String toProductId(JsonNode node, String operation) {
        if (node.isIntegralNumber()) {
            return node.asText();
        }
        if (node.isTextual() && !node.textValue().isBlank()) {
            return node.textValue();
        }
        throw new ProductProviderUnavailableException(
                "Product provider returned an invalid similar product ID while " + operation);
    }
}
