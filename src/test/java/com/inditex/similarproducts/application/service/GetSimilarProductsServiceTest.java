package com.inditex.similarproducts.application.service;

import com.inditex.similarproducts.application.exception.ProductNotFoundException;
import com.inditex.similarproducts.application.exception.ProductProviderTimeoutException;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSimilarProductsServiceTest {

    @Mock
    private ProductProviderPort productProvider;

    private GetSimilarProductsService service;

    @BeforeEach
    void setUp() {
        service = new GetSimilarProductsService(productProvider, 3);
    }

    @Test
    void returnsSimilarProductsInProviderOrder() {
        Product second = product("2");
        Product third = product("3");
        Product fourth = product("4");
        when(productProvider.getSimilarProductIds("1")).thenReturn(Mono.just(List.of("2", "3", "4")));
        when(productProvider.getProduct("2")).thenReturn(Mono.delay(Duration.ofMillis(80)).thenReturn(second));
        when(productProvider.getProduct("3")).thenReturn(Mono.delay(Duration.ofMillis(10)).thenReturn(third));
        when(productProvider.getProduct("4")).thenReturn(Mono.just(fourth));

        StepVerifier.create(service.getSimilarProducts("1"))
                .assertNext(products -> assertThat(products).containsExactly(second, third, fourth))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenThereAreNoSimilarIds() {
        when(productProvider.getSimilarProductIds("1")).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.getSimilarProducts("1"))
                .expectNext(List.of())
                .verifyComplete();

        verify(productProvider, never()).getProduct(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void omitsAnIndividualProductThatIsNotFound() {
        assertIndividualFailureIsOmitted(new ProductNotFoundException("3"));
    }

    @Test
    void omitsAnIndividualProductThatReturnsServerError() {
        assertIndividualFailureIsOmitted(new ProductProviderUnavailableException("HTTP 500"));
    }

    @Test
    void omitsAnIndividualProductThatTimesOut() {
        assertIndividualFailureIsOmitted(
                new ProductProviderTimeoutException("retrieving product 3", new TimeoutException()));
    }

    @Test
    void propagatesUnexpectedFailureOfAnIndividualProduct() {
        when(productProvider.getSimilarProductIds("1")).thenReturn(Mono.just(List.of("2")));
        when(productProvider.getProduct("2")).thenReturn(Mono.error(new IllegalStateException("defect")));

        StepVerifier.create(service.getSimilarProducts("1"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void propagatesFailureFromSimilarIdsEndpoint() {
        ProductProviderUnavailableException failure = new ProductProviderUnavailableException("HTTP 500");
        when(productProvider.getSimilarProductIds("1")).thenReturn(Mono.error(failure));

        StepVerifier.create(service.getSimilarProducts("1"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(productProvider, never()).getProduct(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void neverExceedsConfiguredConcurrency() {
        AtomicInteger activeRequests = new AtomicInteger();
        AtomicInteger maximumActiveRequests = new AtomicInteger();
        service = new GetSimilarProductsService(productProvider, 2);
        List<String> ids = List.of("1", "2", "3", "4", "5");
        when(productProvider.getSimilarProductIds("source")).thenReturn(Mono.just(ids));
        ids.forEach(id -> when(productProvider.getProduct(id)).thenReturn(Mono.defer(() -> {
            int active = activeRequests.incrementAndGet();
            maximumActiveRequests.accumulateAndGet(active, Math::max);
            return Mono.delay(Duration.ofMillis(30))
                    .map(ignored -> {
                        activeRequests.decrementAndGet();
                        return product(id);
                    });
        })));

        StepVerifier.create(service.getSimilarProducts("source"))
                .assertNext(products -> assertThat(products).extracting(Product::id).containsExactlyElementsOf(ids))
                .verifyComplete();

        assertThat(maximumActiveRequests).hasValue(2);
    }

    private void assertIndividualFailureIsOmitted(RuntimeException failure) {
        Product second = product("2");
        Product fourth = product("4");
        when(productProvider.getSimilarProductIds("1")).thenReturn(Mono.just(List.of("2", "3", "4")));
        when(productProvider.getProduct("2")).thenReturn(Mono.just(second));
        when(productProvider.getProduct("3")).thenReturn(Mono.error(failure));
        when(productProvider.getProduct("4")).thenReturn(Mono.just(fourth));

        StepVerifier.create(service.getSimilarProducts("1"))
                .assertNext(products -> assertThat(products).containsExactly(second, fourth))
                .verifyComplete();
    }

    private Product product(String id) {
        return new Product(id, "Product " + id, new BigDecimal("19.99"), true);
    }
}
