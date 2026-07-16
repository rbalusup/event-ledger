package com.schwab.eventledger.gateway.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {

    private final MeterRegistry meterRegistry;

    public EventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEventSubmitted(String outcome) {
        meterRegistry.counter("gateway.events.submitted", "outcome", outcome).increment();
    }
}
