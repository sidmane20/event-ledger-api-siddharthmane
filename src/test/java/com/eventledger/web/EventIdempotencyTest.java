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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static final String EVENT = """
            {
              "eventId": "evt-001",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z",
              "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
            }
            """;

    @Test
    void firstSubmission_returns201WithLocation() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/events/evt-001"))
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    @Test
    void duplicateSubmission_returns200_doesNotDuplicate_andLeavesBalanceUnchanged() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isCreated());

        // Re-deliver the same event: must be treated as a duplicate, returning the original.
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", "/events/evt-001"))
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.amount").value(150.00));

        // Exactly one row stored, and the balance reflects a single credit.
        assertThat(repository.existsByEventId("evt-001")).isTrue();
        assertThat(repository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-123")).hasSize(1);
        assertThat(repository.computeBalance("acct-123")).isEqualByComparingTo("150.00");
    }
}
