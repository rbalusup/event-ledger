package com.schwab.eventledger.gateway.web;

import com.schwab.eventledger.gateway.dto.CreateEventRequest;
import com.schwab.eventledger.gateway.dto.EventResponse;
import com.schwab.eventledger.gateway.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "Submit a transaction event",
            description = "Idempotent by eventId: resubmitting the same eventId returns the original event with "
                    + "duplicate=true and 200 OK. Returns 503 if the Account Service is unavailable, without "
                    + "persisting anything locally, so the same eventId can be safely retried later.")
    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.submitEvent(request);
        HttpStatus status = response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "Get a single event by id")
    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable String id) {
        return eventService.getEvent(id);
    }

    @Operation(summary = "List events for an account",
            description = "Ordered by eventTimestamp ascending, regardless of the order events arrived in. "
                    + "Served entirely from the Gateway's local data, so it keeps working even if the Account "
                    + "Service is unavailable.")
    @GetMapping
    public List<EventResponse> listEvents(@RequestParam("account") String accountId) {
        return eventService.listEventsForAccount(accountId);
    }
}
