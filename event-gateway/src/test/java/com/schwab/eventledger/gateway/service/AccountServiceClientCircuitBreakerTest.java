package com.schwab.eventledger.gateway.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.schwab.eventledger.gateway.domain.EventType;
import com.schwab.eventledger.gateway.dto.ApplyTransactionRequest;
import com.schwab.eventledger.gateway.exception.AccountServiceUnavailableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "resilience4j.circuitbreaker.instances.accountService.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state=1s",
        "resilience4j.circuitbreaker.instances.accountService.permitted-number-of-calls-in-half-open-state=1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountServiceClientCircuitBreakerTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private AccountServiceClient accountServiceClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void opensAfterRepeatedFailuresAndFailsFastWithoutCallingDownstream() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            String eventId = "evt-" + i;
            assertThatThrownBy(() -> accountServiceClient.applyTransaction("acct-1", request(eventId)))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }

        wireMockServer.resetRequests();

        // Breaker should now be OPEN: further calls fail immediately via the fallback,
        // without ever reaching WireMock.
        for (int i = 4; i < 8; i++) {
            String eventId = "evt-" + i;
            assertThatThrownBy(() -> accountServiceClient.applyTransaction("acct-1", request(eventId)))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }

        wireMockServer.verify(0, WireMock.postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
    }

    @Test
    void recoversToClosedAfterWaitDurationOnceDownstreamSucceeds() throws InterruptedException {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            String eventId = "recover-" + i;
            assertThatThrownBy(() -> accountServiceClient.applyTransaction("acct-2", request(eventId)))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }

        Thread.sleep(1200);

        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"eventId":"recover-ok","accountId":"acct-2","type":"CREDIT","amount":10.00,
                                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z",
                                 "appliedAt":"2026-05-15T14:02:12Z","alreadyApplied":false}
                                """)));

        var result = accountServiceClient.applyTransaction("acct-2", request("recover-ok"));

        assertThat(result.eventId()).isEqualTo("recover-ok");
    }

    private ApplyTransactionRequest request(String eventId) {
        return new ApplyTransactionRequest(eventId, EventType.CREDIT, BigDecimal.TEN, "USD", Instant.now());
    }
}
