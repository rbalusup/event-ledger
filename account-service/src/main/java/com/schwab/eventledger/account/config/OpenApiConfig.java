package com.schwab.eventledger.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Account Service API")
                .description("Internal service owning account balances and transaction history. Called only by the Event Gateway.")
                .version("v1"));
    }
}
