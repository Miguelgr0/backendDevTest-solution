package com.inditex.similarproducts.infrastructure.adapter.in.rest;

import com.inditex.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.inditex.similarproducts.domain.model.Product;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Inbound HTTP adapter for the similar-products use case. Aggregation and failure policy remain in
 * the application layer.
 */
@RestController
@RequestMapping("/product")
public class SimilarProductsController {

    private final GetSimilarProductsUseCase getSimilarProductsUseCase;

    public SimilarProductsController(GetSimilarProductsUseCase getSimilarProductsUseCase) {
        this.getSimilarProductsUseCase = getSimilarProductsUseCase;
    }

    /**
     * Returns the available similar products using the order defined by the provider.
     *
     * @param productId source product identifier taken from the path
     * @return reactive ordered response body
     */
    @GetMapping(value = "/{productId}/similar", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Product>> getSimilarProducts(@PathVariable String productId) {
        return getSimilarProductsUseCase.getSimilarProducts(productId);
    }
}
