package com.inditex.similarproducts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point. Configuration properties are discovered centrally so adapters can be
 * configured without leaking Spring concerns into the domain or application layers.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SimilarProductsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimilarProductsApplication.class, args);
    }
}
