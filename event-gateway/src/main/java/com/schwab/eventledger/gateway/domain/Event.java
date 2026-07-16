package com.schwab.eventledger.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "event",
        indexes = {
                @Index(name = "idx_event_account_ts", columnList = "account_id, event_timestamp")
        }
)
public class Event {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private EventType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Lob
    @Column(name = "metadata")
    private String metadataJson;

    protected Event() {
        // JPA
    }

    public Event(String eventId, String accountId, EventType type, BigDecimal amount, String currency,
                 Instant eventTimestamp, Instant receivedAt, String metadataJson) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.receivedAt = receivedAt;
        this.metadataJson = metadataJson;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public EventType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
