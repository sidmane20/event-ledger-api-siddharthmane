package com.eventledger.web;

import com.eventledger.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventRetrievalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    /** Submits an event with the given id, account and ISO timestamp (always a CREDIT of 10.00). */
    private void submit(String eventId, String accountId, String timestamp) throws Exception {
        String body = """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "%s"
                }
                """.formatted(eventId, accountId, timestamp);
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void getById_returnsEvent_whenItExists() throws Exception {
        submit("evt-1", "acct-1", "2026-05-15T10:00:00Z");

        mockMvc.perform(get("/events/evt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.accountId").value("acct-1"));
    }

    @Test
    void amount_isAlwaysRenderedWithTwoDecimals() throws Exception {
        // Submit a whole-number amount; it must come back as 10.00, not 10 or 10.0.
        String body = """
                {
                  "eventId": "evt-money",
                  "accountId": "acct-1",
                  "type": "CREDIT",
                  "amount": 10,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        String json = mockMvc.perform(get("/events/evt-money"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(json).contains("\"amount\":10.00");
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/events/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listByAccount_returnsChronologicalOrder_regardlessOfArrivalOrder() throws Exception {
        // Submitted out of chronological order on purpose.
        submit("evt-late", "acct-1", "2026-05-15T12:00:00Z");
        submit("evt-early", "acct-1", "2026-05-15T08:00:00Z");
        submit("evt-mid", "acct-1", "2026-05-15T10:00:00Z");
        // A different account's event must not appear.
        submit("evt-other", "acct-2", "2026-05-15T09:00:00Z");

        mockMvc.perform(get("/events").param("account", "acct-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].eventId").value("evt-early"))
                .andExpect(jsonPath("$.content[1].eventId").value("evt-mid"))
                .andExpect(jsonPath("$.content[2].eventId").value("evt-late"));
    }

    @Test
    void listByAccount_returnsEmptyPage_forUnknownAccount() throws Exception {
        mockMvc.perform(get("/events").param("account", "no-such-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
