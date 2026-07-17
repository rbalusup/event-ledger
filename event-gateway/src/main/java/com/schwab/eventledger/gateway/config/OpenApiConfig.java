package com.schwab.eventledger.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventGatewayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Event Gateway API")
                .description("Public-facing entry point for submitting and querying financial transaction events.")
                .version("v1"));
    }
}
