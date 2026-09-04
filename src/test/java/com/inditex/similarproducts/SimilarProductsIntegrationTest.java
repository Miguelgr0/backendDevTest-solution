package com.inditex.similarproducts;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "debug=false"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SimilarProductsIntegrationTest {

    private static final MockWebServer SERVER = new MockWebServer();

    static {
        try {
            SERVER.setDispatcher(new ProductDispatcher());
            SERVER.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final WebTestClient client;

    SimilarProductsIntegrationTest(WebTestClient client) {
        this.client = client;
    }

    @AfterAll
    static void stopServer() throws IOException {
        SERVER.shutdown();
    }

    @DynamicPropertySource
    static void externalApiProperties(DynamicPropertyRegistry registry) {
        registry.add("external-api.base-url", () -> SERVER.url("/").toString());
        registry.add("external-api.connect-timeout", () -> "1s");
        registry.add("external-api.response-timeout", () -> "1s");
    }

    @Test
    void wiresTheCompleteRequestFlowAndKeepsPartialResults() {
        client.get()
                .uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[1].id").isEqualTo("3")
                .jsonPath("$[2]").doesNotExist();
    }

    private static final class ProductDispatcher extends Dispatcher {

        @NotNull
        @Override
        public MockResponse dispatch(@NotNull RecordedRequest request) {
            return switch (request.getPath()) {
                case "/product/1/similarids" -> json(200, "[2,3,5]");
                case "/product/2" -> json(200,
                        "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}");
                case "/product/3" -> json(200,
                        "{\"id\":\"3\",\"name\":\"Blazer\",\"price\":29.99,\"availability\":false}");
                case "/product/5" -> json(404, "{\"message\":\"Product not found\"}");
                default -> json(404, "{}");
            };
        }

        private MockResponse json(int status, String body) {
            return new MockResponse()
                    .setResponseCode(status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }
    }
}
