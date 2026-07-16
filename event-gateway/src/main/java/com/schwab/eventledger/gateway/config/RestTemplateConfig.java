package com.schwab.eventledger.gateway.config;

import com.schwab.eventledger.gateway.tracing.TraceIdRestTemplateInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate accountServiceRestTemplate(RestTemplateBuilder builder,
                                                     TraceIdRestTemplateInterceptor traceIdRestTemplateInterceptor) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(2))
                .additionalInterceptors(traceIdRestTemplateInterceptor)
                .build();
    }
}
