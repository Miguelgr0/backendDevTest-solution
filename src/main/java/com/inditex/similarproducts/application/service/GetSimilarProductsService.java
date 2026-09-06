package com.inditex.similarproducts.application.service;

import com.inditex.similarproducts.application.exception.ProductProviderException;
import com.inditex.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.inditex.similarproducts.application.port.out.ProductProviderPort;
import com.inditex.similarproducts.domain.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the similar-products use case without depending on transport or caching details.
 */
public final class GetSimilarProductsService implements GetSimilarProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetSimilarProductsService.class);

    private final ProductProviderPort productProvider;
    private final int maxConcurrency;

    public GetSimilarProductsService(ProductProviderPort productProvider, int maxConcurrency) {
        this.productProvider = Objects.requireNonNull(productProvider);
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("Maximum concurrency must be greater than zero");
        }
        this.maxConcurrency = maxConcurrency;
    }

    @Override
    public Mono<List<Product>> getSimilarProducts(String productId) {
        return productProvider.getSimilarProductIds(productId)
                .flatMapMany(Flux::fromIterable)
                // Subscribe concurrently, but buffer completed publishers until preceding IDs have
                // emitted. The third argument keeps per-inner prefetch deliberately small.
                .flatMapSequential(this::getProductSafely, maxConcurrency, 1)
                .collectList();
    }

    private Mono<Product> getProductSafely(String productId) {
        return productProvider.getProduct(productId)
                // Only provider failures are expected and degrade to a partial result. Anything else
                // is a defect in this service and must surface instead of silently losing a product.
                .onErrorResume(ProductProviderException.class, error -> {
                    // Recovery is scoped to this inner publisher so a failure cannot cancel other
                    // details or hide a similar-IDs failure.
                    log.debug("Omitting similar product {} because it could not be retrieved: {}",
                            productId, error.getMessage());
                    return Mono.empty();
                });
    }
}
