package com.inditex.similarproducts.infrastructure.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.inditex.similarproducts.application.exception.ProductProviderUnavailableException;
import com.inditex.similarproducts.application.port.out.ProductProviderPort;
import com.inditex.similarproducts.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachingProductProviderAdapterTest {

    @Mock
    private ProductProviderPort delegate;

    private Cache<String, Product> cache;
    private CachingProductProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofMinutes(1)).build();
        adapter = new CachingProductProviderAdapter(delegate, cache);
    }

    @Test
    void cachesSuccessfulProductResponses() {
        Product product = product("2");
        when(delegate.getProduct("2")).thenReturn(Mono.just(product));

        StepVerifier.create(adapter.getProduct("2").then(adapter.getProduct("2")))
                .expectNext(product)
                .verifyComplete();

        verify(delegate).getProduct("2");
    }

    @Test
    void coalescesConcurrentRequestsForTheSameProduct() {
        Product product = product("2");
        when(delegate.getProduct("2")).thenReturn(Mono.delay(Duration.ofMillis(50)).thenReturn(product));

        StepVerifier.create(Mono.zip(
                        adapter.getProduct("2"),
                        adapter.getProduct("2"),
                        adapter.getProduct("2")))
                .expectNextCount(1)
                .verifyComplete();

        verify(delegate).getProduct("2");
    }

    @Test
    void doesNotCacheFailures() {
        ProductProviderUnavailableException failure = new ProductProviderUnavailableException("HTTP 500");
        when(delegate.getProduct("6"))
                .thenReturn(Mono.error(failure))
                .thenReturn(Mono.error(failure));

        StepVerifier.create(adapter.getProduct("6"))
                .expectError(ProductProviderUnavailableException.class)
                .verify();
        StepVerifier.create(adapter.getProduct("6"))
                .expectError(ProductProviderUnavailableException.class)
                .verify();

        verify(delegate, times(2)).getProduct("6");
    }

    @Test
    void reloadsAProductAfterItsCacheEntryExpires() {
        AtomicLong ticker = new AtomicLong();
        cache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(Duration.ofMinutes(1))
                .ticker(ticker::get)
                .build();
        adapter = new CachingProductProviderAdapter(delegate, cache);
        Product product = product("2");
        when(delegate.getProduct("2")).thenReturn(Mono.just(product));

        StepVerifier.create(adapter.getProduct("2"))
                .expectNext(product)
                .verifyComplete();
        ticker.addAndGet(Duration.ofMinutes(2).toNanos());
        StepVerifier.create(adapter.getProduct("2"))
                .expectNext(product)
                .verifyComplete();

        verify(delegate, times(2)).getProduct("2");
    }

    @Test
    void neverCachesSimilarIdLists() {
        when(delegate.getSimilarProductIds("1"))
                .thenReturn(Mono.just(List.of("2")))
                .thenReturn(Mono.just(List.of("3")));

        StepVerifier.create(adapter.getSimilarProductIds("1"))
                .expectNext(List.of("2"))
                .verifyComplete();
        StepVerifier.create(adapter.getSimilarProductIds("1"))
                .expectNext(List.of("3"))
                .verifyComplete();

        verify(delegate, times(2)).getSimilarProductIds("1");
    }

    private Product product(String id) {
        return new Product(id, "Product " + id, new BigDecimal("19.99"), true);
    }
}
