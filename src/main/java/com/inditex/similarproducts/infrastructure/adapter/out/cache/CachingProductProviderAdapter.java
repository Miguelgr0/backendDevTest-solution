package com.inditex.similarproducts.infrastructure.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.inditex.similarproducts.application.port.out.ProductProviderPort;
import com.inditex.similarproducts.domain.model.Product;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Infrastructure decorator that caches successful product details and coalesces concurrent misses.
 * Similar-ID lists pass straight through because their freshness and reuse characteristics differ
 * from immutable-looking product details.
 */
public final class CachingProductProviderAdapter implements ProductProviderPort {

    private final ProductProviderPort delegate;
    private final Cache<String, Product> productCache;

    // One shared publisher per missing key prevents a burst of callers from creating duplicate HTTP
    // requests. Entries live only for the duration of a load and therefore never cache failures.
    private final Map<String, Mono<Product>> inFlightRequests = new ConcurrentHashMap<>();

    public CachingProductProviderAdapter(ProductProviderPort delegate, Cache<String, Product> productCache) {
        this.delegate = delegate;
        this.productCache = productCache;
    }

    @Override
    public Mono<List<String>> getSimilarProductIds(String productId) {
        return delegate.getSimilarProductIds(productId);
    }

    @Override
    public Mono<Product> getProduct(String productId) {
        return Mono.defer(() -> {
            Product cachedProduct = productCache.getIfPresent(productId);
            if (cachedProduct != null) {
                return Mono.just(cachedProduct);
            }
            return inFlightRequests.computeIfAbsent(productId, this::loadProduct);
        });
    }

    private Mono<Product> loadProduct(String productId) {
        return delegate.getProduct(productId)
                .doOnNext(product -> productCache.put(productId, product))
                .doFinally(signalType -> inFlightRequests.remove(productId))
                // Share this single subscription among callers that observed the same cache miss.
                .cache();
    }
}
