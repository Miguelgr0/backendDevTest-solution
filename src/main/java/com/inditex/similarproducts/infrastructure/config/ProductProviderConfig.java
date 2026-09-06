package com.inditex.similarproducts.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.inditex.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.inditex.similarproducts.application.port.out.ProductProviderPort;
import com.inditex.similarproducts.application.service.GetSimilarProductsService;
import com.inditex.similarproducts.domain.model.Product;
import com.inditex.similarproducts.infrastructure.adapter.out.cache.CachingProductProviderAdapter;
import com.inditex.similarproducts.infrastructure.adapter.out.http.ProductApiAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Composition root for the outbound provider chain and the application use case.
 */
@Configuration
public class ProductProviderConfig {

    /**
     * Creates a bounded cache that expires entries after their successful write. Its statistics are
     * published to Micrometer so hit ratio and evictions are observable at runtime instead of
     * being an untested assumption.
     */
    @Bean
    Cache<String, Product> productCache(ProductCacheProperties properties, MeterRegistry meterRegistry) {
        Cache<String, Product> cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.ttl())
                .recordStats()
                .build();

        return CaffeineCacheMetrics.monitor(meterRegistry, cache, "products");
    }

    @Bean
    ProductApiAdapter productApiAdapter(WebClient productWebClient) {
        return new ProductApiAdapter(productWebClient);
    }

    @Bean
    ProductProviderPort productProvider(ProductApiAdapter productApiAdapter, Cache<String, Product> productCache) {
        // The decorator keeps cache policy in infrastructure while exposing the same application port.
        return new CachingProductProviderAdapter(productApiAdapter, productCache);
    }

    @Bean
    GetSimilarProductsUseCase getSimilarProductsUseCase(
            ProductProviderPort productProvider,
            SimilarProductsProperties properties
    ) {
        return new GetSimilarProductsService(productProvider, properties.maxConcurrency());
    }
}
