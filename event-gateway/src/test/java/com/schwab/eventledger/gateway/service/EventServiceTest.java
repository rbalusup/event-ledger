package com.schwab.eventledger.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.eventledger.gateway.domain.EventType;
import com.schwab.eventledger.gateway.dto.CreateEventRequest;
import com.schwab.eventledger.gateway.dto.EventResponse;
import com.schwab.eventledger.gateway.exception.EventNotFoundException;
import com.schwab.eventledger.gateway.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EventServiceTest {

    @Autowired
    private EventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void resubmittingSameEventIdReturnsOriginalAndIsMarkedDuplicate() {
        EventService eventService = new EventService(eventRepository, objectMapper);
        CreateEventRequest request = request("evt-1", "acct-1", EventType.CREDIT, "150.00", Instant.now(), null);

        EventResponse first = eventService.submitEvent(request);
        EventResponse second = eventService.submitEvent(request);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-1")).hasSize(1);
    }

    @Test
    void listEventsForAccountAreOrderedByEventTimestampRegardlessOfArrivalOrder() {
        EventService eventService = new EventService(eventRepository, objectMapper);
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        eventService.submitEvent(request("evt-b", "acct-2", EventType.CREDIT, "50.00", base.plusSeconds(5), null));
        eventService.submitEvent(request("evt-a", "acct-2", EventType.DEBIT, "20.00", base, null));
        eventService.submitEvent(request("evt-c", "acct-2", EventType.CREDIT, "30.00", base.plusSeconds(10), null));

        var events = eventService.listEventsForAccount("acct-2");

        assertThat(events).extracting(EventResponse::eventId).containsExactly("evt-a", "evt-b", "evt-c");
    }

    @Test
    void metadataRoundTripsThroughStorage() {
        EventService eventService = new EventService(eventRepository, objectMapper);
        Map<String, Object> metadata = Map.of("source", "mainframe-batch", "batchId", "B-9042");

        EventResponse response = eventService.submitEvent(
                request("evt-meta", "acct-3", EventType.CREDIT, "10.00", Instant.now(), metadata));

        assertThat(response.metadata()).containsEntry("source", "mainframe-batch").containsEntry("batchId", "B-9042");
    }

    @Test
    void gettingUnknownEventThrowsNotFound() {
        EventService eventService = new EventService(eventRepository, objectMapper);

        assertThatThrownBy(() -> eventService.getEvent("does-not-exist"))
                .isInstanceOf(EventNotFoundException.class);
    }

    private CreateEventRequest request(String eventId, String accountId, EventType type, String amount,
                                        Instant eventTimestamp, Map<String, Object> metadata) {
        return new CreateEventRequest(eventId, accountId, type, new BigDecimal(amount), "USD", eventTimestamp, metadata);
    }
}
