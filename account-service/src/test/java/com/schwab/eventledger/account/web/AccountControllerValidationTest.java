package com.schwab.eventledger.account.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.eventledger.account.dto.BalanceResponse;
import com.schwab.eventledger.account.exception.AccountNotFoundException;
import com.schwab.eventledger.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        String body = "{\"amount\": 10.00, \"currency\": \"USD\", \"eventTimestamp\": \"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        String body = "{\"eventId\": \"evt-1\", \"type\": \"CREDIT\", \"amount\": 0, \"currency\": \"USD\", \"eventTimestamp\": \"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownTransactionType() throws Exception {
        String body = "{\"eventId\": \"evt-1\", \"type\": \"YEET\", \"amount\": 10, \"currency\": \"USD\", \"eventTimestamp\": \"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void returns404ForUnknownAccountBalance() throws Exception {
        when(accountService.getBalance(eq("unknown"))).thenThrow(new AccountNotFoundException("unknown"));

        mockMvc.perform(get("/accounts/unknown/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void returnsBalanceForKnownAccount() throws Exception {
        when(accountService.getBalance(eq("acct-1")))
                .thenReturn(new BalanceResponse("acct-1", new BigDecimal("120.50"), "USD"));

        mockMvc.perform(get("/accounts/acct-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(120.50));
    }
}
