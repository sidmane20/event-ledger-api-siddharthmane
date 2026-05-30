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
class PaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    /** Submits 5 events for acct-1 with ascending hours, but in shuffled arrival order. */
    private void seedFiveShuffled() throws Exception {
        submit("e3", "2026-05-15T03:00:00Z");
        submit("e1", "2026-05-15T01:00:00Z");
        submit("e5", "2026-05-15T05:00:00Z");
        submit("e2", "2026-05-15T02:00:00Z");
        submit("e4", "2026-05-15T04:00:00Z");
    }

    private void submit(String eventId, String timestamp) throws Exception {
        String body = """
                {
                  "eventId": "%s",
                  "accountId": "acct-1",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "%s"
                }
                """.formatted(eventId, timestamp);
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void firstPage_isChronologicalAndCarriesPageMetadata() throws Exception {
        seedFiveShuffled();

        mockMvc.perform(get("/events").param("account", "acct-1").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].eventId").value("e1"))
                .andExpect(jsonPath("$.content[1].eventId").value("e2"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void ordering_isPreservedAcrossPageBoundaries() throws Exception {
        seedFiveShuffled();

        mockMvc.perform(get("/events").param("account", "acct-1").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("e3"))
                .andExpect(jsonPath("$.content[1].eventId").value("e4"));

        mockMvc.perform(get("/events").param("account", "acct-1").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value("e5"))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidPageSize_returns400() throws Exception {
        mockMvc.perform(get("/events").param("account", "acct-1").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));

        mockMvc.perform(get("/events").param("account", "acct-1").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
