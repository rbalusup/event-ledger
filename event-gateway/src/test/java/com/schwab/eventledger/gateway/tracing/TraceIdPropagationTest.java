package com.schwab.eventledger.gateway.tracing;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceIdPropagationTest {

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
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void propagatesCallersTraceIdToAccountServiceAndEchoesItInTheResponse() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"eventId":"evt-trace-1","accountId":"acct-trace","type":"CREDIT","amount":10.00,
                                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z",
                                 "appliedAt":"2026-05-15T14:02:12Z","alreadyApplied":false}
                                """)));

        HttpHeaders headers = new HttpHeaders();
        headers.add(TraceIdFilter.TRACE_ID_HEADER, "caller-supplied-trace-id");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = """
                {"eventId":"evt-trace-1","accountId":"acct-trace","type":"CREDIT","amount":10.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/events", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("caller-supplied-trace-id");
        wireMockServer.verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
                .withHeader(TraceIdFilter.TRACE_ID_HEADER, matching("caller-supplied-trace-id")));
    }

    @Test
    void generatesATraceIdWhenCallerDoesNotSupplyOneAndPropagatesIt() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"eventId":"evt-trace-2","accountId":"acct-trace","type":"CREDIT","amount":10.00,
                                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z",
                                 "appliedAt":"2026-05-15T14:02:12Z","alreadyApplied":false}
                                """)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = """
                {"eventId":"evt-trace-2","accountId":"acct-trace","type":"CREDIT","amount":10.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/events", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        String generatedTraceId = response.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(generatedTraceId).isNotBlank();
        wireMockServer.verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
                .withHeader(TraceIdFilter.TRACE_ID_HEADER, matching(generatedTraceId)));
    }
}
