package com.schwab.eventledger.gateway.web;

import com.schwab.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.schwab.eventledger.gateway.exception.EventNotFoundException;
import com.schwab.eventledger.gateway.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        String body = "{\"amount\": 10.00, \"currency\": \"USD\", \"eventTimestamp\": \"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        String body = "{\"eventId\":\"evt-1\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\",\"amount\":-5,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownEventType() throws Exception {
        String body = "{\"eventId\":\"evt-1\",\"accountId\":\"acct-1\",\"type\":\"REFUND\",\"amount\":5,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsInvalidCurrencyFormat() throws Exception {
        String body = "{\"eventId\":\"evt-1\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\",\"amount\":5,\"currency\":\"us\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void returns404ForUnknownEvent() throws Exception {
        when(eventService.getEvent(eq("missing"))).thenThrow(new EventNotFoundException("missing"));

        mockMvc.perform(get("/events/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_FOUND"));
    }

    @Test
    void returns503WhenAccountServiceIsUnavailable() throws Exception {
        when(eventService.submitEvent(any()))
                .thenThrow(new AccountServiceUnavailableException("Account service is unavailable, please retry later",
                        new RuntimeException("connection refused")));
        String body = "{\"eventId\":\"evt-1\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\",\"amount\":5,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ACCOUNT_SERVICE_UNAVAILABLE"));
    }
}
