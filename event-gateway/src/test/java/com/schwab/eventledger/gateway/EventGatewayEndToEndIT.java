package com.schwab.eventledger.gateway;

import com.schwab.eventledger.account.AccountServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True end-to-end test: a real Account Service instance runs in-process (its own
 * Spring context, own embedded H2, own random port) alongside the Gateway's own
 * @SpringBootTest context, and the two talk over real HTTP - no WireMock involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventGatewayEndToEndIT {

    // Started via a static field initializer (not @BeforeAll) so it is guaranteed to be
    // up before Spring resolves @DynamicPropertySource values while preparing this
    // class's own ApplicationContext.
    private static final ConfigurableApplicationContext ACCOUNT_SERVICE_CONTEXT =
            new SpringApplicationBuilder(AccountServiceApplication.class)
                    .properties(Map.of("server.port", "0"))
                    .run();

    private static final RestTemplate ACCOUNT_SERVICE_REST_TEMPLATE = new RestTemplate();

    @Autowired
    private TestRestTemplate gatewayRestTemplate;

    @AfterAll
    static void stopAccountService() {
        ACCOUNT_SERVICE_CONTEXT.close();
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        String port = ACCOUNT_SERVICE_CONTEXT.getEnvironment().getProperty("local.server.port");
        registry.add("account-service.base-url", () -> "http://localhost:" + port);
    }

    @Test
    void submittingAnEventAppliesItOnTheRealAccountServiceAndIsIdempotentOnResubmission() {
        String accountServicePort = ACCOUNT_SERVICE_CONTEXT.getEnvironment().getProperty("local.server.port");
        String accountId = "acct-e2e-1";
        String body = """
                {"eventId":"evt-e2e-1","accountId":"%s","type":"CREDIT","amount":150.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """.formatted(accountId);

        var createResponse = gatewayRestTemplate.postForEntity("/events", jsonEntity(body), String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var balanceResponse = ACCOUNT_SERVICE_REST_TEMPLATE.getForEntity(
                "http://localhost:" + accountServicePort + "/accounts/" + accountId + "/balance", String.class);
        assertThat(balanceResponse.getBody()).contains("150.0");

        var duplicateResponse = gatewayRestTemplate.postForEntity("/events", jsonEntity(body), String.class);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicateResponse.getBody()).contains("\"duplicate\":true");

        var balanceAfterDuplicate = ACCOUNT_SERVICE_REST_TEMPLATE.getForEntity(
                "http://localhost:" + accountServicePort + "/accounts/" + accountId + "/balance", String.class);
        assertThat(balanceAfterDuplicate.getBody()).contains("150.0");

        var gatewayListing = gatewayRestTemplate.getForEntity("/events?account=" + accountId, String.class);
        assertThat(gatewayListing.getBody()).contains("evt-e2e-1");
    }

    private org.springframework.http.HttpEntity<String> jsonEntity(String body) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
