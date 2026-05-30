package com.eventledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. The full spec is generated from the controllers by springdoc and is
 * served at {@code /v3/api-docs}; an interactive Swagger UI is available at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventLedgerOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Event Ledger API")
                .description("Ingests financial transaction events that may arrive out of order or more "
                        + "than once, and serves per-account event listings and computed balances.")
                .version("v1"));
    }
}
