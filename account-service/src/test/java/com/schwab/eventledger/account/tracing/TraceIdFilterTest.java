package com.schwab.eventledger.account.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceIdFilterTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void generatesATraceIdWhenCallerDoesNotSupplyOne() {
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

        String traceId = response.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(traceId).isNotBlank();
    }

    @Test
    void preservesTheCallersTraceIdUnchanged() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TraceIdFilter.TRACE_ID_HEADER, "caller-supplied-trace-id");

        ResponseEntity<String> response = restTemplate.exchange(
                "/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("caller-supplied-trace-id");
    }
}
