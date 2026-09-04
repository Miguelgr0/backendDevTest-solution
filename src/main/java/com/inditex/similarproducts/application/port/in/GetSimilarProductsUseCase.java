package com.inditex.similarproducts.application.port.in;

import com.inditex.similarproducts.domain.model.Product;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Inbound contract for retrieving the ordered details of products similar to a source product.
 */
public interface GetSimilarProductsUseCase {

    /**
     * Retrieves all available similar-product details in descending similarity order.
     * Individual details that cannot be retrieved are absent from the result. A failure to obtain
     * the initial similarity list is propagated because the operation cannot be fulfilled.
     *
     * @param productId source product identifier
     * @return a lazy publisher containing an ordered, possibly empty list
     */
    Mono<List<Product>> getSimilarProducts(String productId);
}
