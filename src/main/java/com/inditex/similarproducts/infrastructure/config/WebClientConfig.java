package com.inditex.similarproducts.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the non-blocking HTTP client used by the outbound product adapter.
 */
@Configuration
public class WebClientConfig {

    /**
     * Applies both connection establishment and response/read limits at the Reactor Netty layer.
     * Keeping these limits in the connector also covers calls made outside the application service.
     */
    @Bean
    WebClient productWebClient(WebClient.Builder builder, ExternalApiProperties properties) {
        int connectTimeoutMillis = Math.toIntExact(properties.connectTimeout().toMillis());

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(properties.responseTimeout());

        return builder
                .baseUrl(properties.baseUrl().toString())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
