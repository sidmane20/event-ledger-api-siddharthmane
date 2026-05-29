package com.eventledger.web;

import com.eventledger.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private org.springframework.test.web.servlet.ResultActions postEvent(String body) throws Exception {
        return mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void missingRequiredField_returns400_withFieldError() throws Exception {
        // accountId omitted.
        String body = """
                {
                  "eventId": "evt-1",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
        postEvent(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'accountId')]").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void zeroAmount_returns400() throws Exception {
        String body = """
                {
                  "eventId": "evt-1",
                  "accountId": "acct-1",
                  "type": "CREDIT",
                  "amount": 0,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
        postEvent(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());
    }

    @Test
    void negativeAmount_returns400() throws Exception {
        String body = """
                {
                  "eventId": "evt-1",
                  "accountId": "acct-1",
                  "type": "DEBIT",
                  "amount": -5.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
        postEvent(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());
    }

    @Test
    void unknownEventType_returns400() throws Exception {
        String body = """
                {
                  "eventId": "evt-1",
                  "accountId": "acct-1",
                  "type": "TRANSFER",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
        postEvent(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(repository.count()).isZero();
    }

    @Test
    void malformedJson_returns400() throws Exception {
        postEvent("{ this is not valid json ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void invalidTimestamp_returns400() throws Exception {
        String body = """
                {
                  "eventId": "evt-1",
                  "accountId": "acct-1",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "not-a-timestamp"
                }
                """;
        postEvent(body).andExpect(status().isBadRequest());
    }

    @Test
    void listing_withoutAccountParam_returns400() throws Exception {
        mockMvc.perform(get("/events"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing parameter"));
    }
}
