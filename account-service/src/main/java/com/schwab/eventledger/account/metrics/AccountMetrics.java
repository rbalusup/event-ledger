package com.schwab.eventledger.account.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AccountMetrics {

    private final MeterRegistry meterRegistry;

    public AccountMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTransactionApplied(String outcome) {
        meterRegistry.counter("account.transactions.applied", "outcome", outcome).increment();
    }
}
