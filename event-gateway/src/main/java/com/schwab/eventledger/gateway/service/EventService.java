package com.schwab.eventledger.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.eventledger.gateway.domain.Event;
import com.schwab.eventledger.gateway.dto.ApplyTransactionRequest;
import com.schwab.eventledger.gateway.dto.CreateEventRequest;
import com.schwab.eventledger.gateway.dto.EventResponse;
import com.schwab.eventledger.gateway.exception.EventNotFoundException;
import com.schwab.eventledger.gateway.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    public EventService(EventRepository eventRepository, AccountServiceClient accountServiceClient,
                         ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
    }

    public EventResponse submitEvent(CreateEventRequest request) {
        var existing = eventRepository.findById(request.eventId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        // Apply downstream first; only persist locally once the Account Service has
        // confirmed the transaction, so a failed call leaves nothing behind for a
        // legitimate client retry with the same eventId to conflict with.
        accountServiceClient.applyTransaction(request.accountId(), new ApplyTransactionRequest(
                request.eventId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp()
        ));

        Event event = new Event(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now(),
                writeMetadata(request.metadata())
        );

        Event saved = eventRepository.save(event);
        return toResponse(saved, false);
    }

    public EventResponse getEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toResponse(event, false);
    }

    public List<EventResponse> listEventsForAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId).stream()
                .map(e -> toResponse(e, false))
                .toList();
    }

    private EventResponse toResponse(Event event, boolean duplicate) {
        return EventResponse.from(event, readMetadata(event.getMetadataJson()), duplicate);
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("metadata could not be serialized", e);
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored metadata could not be deserialized", e);
        }
    }
}
