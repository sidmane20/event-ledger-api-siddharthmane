package com.eventledger.web;

import com.eventledger.repository.EventRepository;
import com.eventledger.web.dto.BalanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BalanceComputationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private void submit(String eventId, String accountId, String type, String amount, String timestamp) throws Exception {
        String body = """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "%s",
                  "amount": %s,
                  "currency": "USD",
                  "eventTimestamp": "%s"
                }
                """.formatted(eventId, accountId, type, amount, timestamp);
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private BalanceResponse fetchBalance(String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BalanceResponse.class);
    }

    @Test
    void balance_netsCreditsMinusDebits_regardlessOfArrivalOrder() throws Exception {
        // Arrive out of chronological order; the net must still be correct.
        submit("e1", "acct-1", "CREDIT", "150.00", "2026-05-15T12:00:00Z");
        submit("e2", "acct-1", "DEBIT", "40.50", "2026-05-15T08:00:00Z");
        submit("e3", "acct-1", "CREDIT", "0.50", "2026-05-15T10:00:00Z");

        BalanceResponse balance = fetchBalance("acct-1");

        assertThat(balance.accountId()).isEqualTo("acct-1");
        // 150.00 - 40.50 + 0.50 = 110.00
        assertThat(balance.balance()).isEqualByComparingTo("110.00");
    }

    @Test
    void balance_isZero_forAccountWithNoEvents() throws Exception {
        assertThat(fetchBalance("ghost-account").balance()).isEqualByComparingTo("0");
    }

    @Test
    void balance_canGoNegative_whenDebitsExceedCredits() throws Exception {
        submit("e1", "acct-2", "CREDIT", "30.00", "2026-05-15T08:00:00Z");
        submit("e2", "acct-2", "DEBIT", "50.00", "2026-05-15T09:00:00Z");

        assertThat(fetchBalance("acct-2").balance()).isEqualByComparingTo("-20.00");
    }
}
