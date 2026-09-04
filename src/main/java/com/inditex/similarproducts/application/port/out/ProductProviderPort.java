package com.inditex.similarproducts.application.port.out;

import com.inditex.similarproducts.domain.model.Product;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Outbound boundary for product information. Implementations may use HTTP, a cache, or another
 * source without changing the application service.
 */
public interface ProductProviderPort {

    /**
     * @param productId source product identifier
     * @return similar identifiers in provider-defined similarity order
     */
    Mono<List<String>> getSimilarProductIds(String productId);

    /**
     * @param productId product identifier to resolve
     * @return the resolved product, or an error signal when it cannot be retrieved
     */
    Mono<Product> getProduct(String productId);
}
