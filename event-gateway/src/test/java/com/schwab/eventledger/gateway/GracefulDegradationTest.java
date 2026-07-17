package com.schwab.eventledger.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the assignment's graceful-degradation requirement as one cohesive flow
 * through the real Gateway HTTP layer: once the Account Service is unreachable,
 * POST /events must fail fast with 503 (not hang or 500), while GET /events/{id} and
 * GET /events?account= - which only depend on the Gateway's own local data - must keep
 * working exactly as before.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GracefulDegradationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void readsStayAvailableAndWritesFailFastOnceAccountServiceIsUnreachable() {
        String accountId = "acct-degraded";
        String seededEventId = "evt-degraded-seed";

        // Account Service is healthy: seed one event so the Gateway has local data to
        // still be able to read back once the downstream goes away.
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"eventId":"%s","accountId":"%s","type":"CREDIT","amount":75.00,
                                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z",
                                 "appliedAt":"2026-05-15T14:02:12Z","alreadyApplied":false}
                                """.formatted(seededEventId, accountId))));

        var seedResponse = restTemplate.postForEntity("/events", jsonEntity("""
                {"eventId":"%s","accountId":"%s","type":"CREDIT","amount":75.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """.formatted(seededEventId, accountId)), String.class);
        assertThat(seedResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Account Service goes away entirely - a real outage, not just a slow/error response.
        wireMockServer.stop();

        var writeResponse = restTemplate.postForEntity("/events", jsonEntity("""
                {"eventId":"evt-degraded-new","accountId":"%s","type":"DEBIT","amount":10.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:05:00Z"}
                """.formatted(accountId)), String.class);
        assertThat(writeResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(writeResponse.getBody()).contains("ACCOUNT_SERVICE_UNAVAILABLE");

        var getByIdResponse = restTemplate.getForEntity("/events/" + seededEventId, String.class);
        assertThat(getByIdResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getByIdResponse.getBody()).contains(seededEventId);

        var listResponse = restTemplate.getForEntity("/events?account=" + accountId, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains(seededEventId);

        // The failed write must not have persisted anything locally either.
        var missingEventResponse = restTemplate.getForEntity("/events/evt-degraded-new", String.class);
        assertThat(missingEventResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity<String> jsonEntity(String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
