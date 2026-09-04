package com.inditex.similarproducts.infrastructure.adapter.out.http;

import com.inditex.similarproducts.application.exception.ProductNotFoundException;
import com.inditex.similarproducts.application.exception.ProductProviderTimeoutException;
import com.inditex.similarproducts.application.exception.ProductProviderUnavailableException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProductApiAdapterTest {

    private MockWebServer server;
    private ProductApiAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        adapter = adapterWithTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void readsNumericAndTextualSimilarIds() throws InterruptedException {
        enqueueJson(200, "[2,\"3\",4]");

        StepVerifier.create(adapter.getSimilarProductIds("1"))
                .expectNext(List.of("2", "3", "4"))
                .verifyComplete();

        assertThat(server.takeRequest().getPath()).isEqualTo("/product/1/similarids");
    }

    @Test
    void mapsProductResponseToDomain() throws InterruptedException {
        enqueueJson(200, "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}");

        StepVerifier.create(adapter.getProduct("2"))
                .assertNext(product -> {
                    assertThat(product.id()).isEqualTo("2");
                    assertThat(product.name()).isEqualTo("Dress");
                    assertThat(product.price()).isEqualByComparingTo(new BigDecimal("19.99"));
                    assertThat(product.availability()).isTrue();
                })
                .verifyComplete();

        assertThat(server.takeRequest().getPath()).isEqualTo("/product/2");
    }

    @Test
    void maps404ToNotFound() {
        enqueueJson(404, "{\"message\":\"Product not found\"}");

        StepVerifier.create(adapter.getProduct("5"))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void maps500ToProviderUnavailable() {
        enqueueJson(500, "{\"message\":\"Internal error\"}");

        StepVerifier.create(adapter.getProduct("6"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ProductProviderUnavailableException.class)
                        .hasMessageContaining("HTTP 500"))
                .verify();
    }

    @Test
    void mapsSlowResponseToTimeout() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")
                .setBodyDelay(300, TimeUnit.MILLISECONDS));
        adapter = adapterWithTimeout(Duration.ofMillis(50));

        StepVerifier.create(adapter.getProduct("2"))
                .expectError(ProductProviderTimeoutException.class)
                .verify();
    }

    @Test
    void treatsMalformedProductAsProviderFailure() {
        enqueueJson(200, "{\"id\":\"2\",\"name\":null,\"price\":19.99,\"availability\":true}");

        StepVerifier.create(adapter.getProduct("2"))
                .expectError(ProductProviderUnavailableException.class)
                .verify();
    }

    @Test
    void treatsMissingAvailabilityAsProviderFailure() {
        enqueueJson(200, "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99}");

        StepVerifier.create(adapter.getProduct("2"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ProductProviderUnavailableException.class)
                        .hasMessageContaining("retrieving product 2"))
                .verify();
    }

    @Test
    void rejectsInvalidSimilarIdNodes() {
        enqueueJson(200, "[2,{},null,true,4.5]");

        StepVerifier.create(adapter.getSimilarProductIds("1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ProductProviderUnavailableException.class)
                        .hasMessageContaining("invalid similar product ID"))
                .verify();
    }

    private ProductApiAdapter adapterWithTimeout(Duration timeout) {
        HttpClient client = HttpClient.create().responseTimeout(timeout);
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
        return new ProductApiAdapter(webClient);
    }

    private void enqueueJson(int status, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body));
    }
}
