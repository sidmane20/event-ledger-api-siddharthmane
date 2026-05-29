package com.eventledger.repository;

import com.eventledger.domain.Event;
import com.eventledger.domain.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EventRepositoryTest {

    @Autowired
    private EventRepository repository;

    private static Event event(String eventId, String accountId, EventType type, String amount, Instant ts) {
        return new Event(eventId, accountId, type, new BigDecimal(amount), "USD", ts, null);
    }

    @Test
    void findByEventId_returnsPersistedEvent() {
        Event saved = repository.save(event("evt-1", "acct-1", EventType.CREDIT, "100.00", Instant.parse("2026-05-15T10:00:00Z")));

        assertThat(repository.findByEventId("evt-1")).isPresent();
        assertThat(repository.findByEventId("evt-1").get().getId()).isEqualTo(saved.getId());
        assertThat(repository.findByEventId("missing")).isEmpty();
    }

    @Test
    void receivedAt_isPopulatedOnPersist() {
        Event saved = repository.save(event("evt-1", "acct-1", EventType.CREDIT, "100.00", Instant.parse("2026-05-15T10:00:00Z")));

        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void metadata_roundTripsThroughJsonColumn() {
        Event e = new Event("evt-meta", "acct-1", EventType.CREDIT, new BigDecimal("10.00"), "USD",
                Instant.parse("2026-05-15T10:00:00Z"), Map.of("source", "mainframe-batch", "batchId", "B-9042"));
        repository.saveAndFlush(e);

        Map<String, Object> reloaded = repository.findByEventId("evt-meta").orElseThrow().getMetadata();
        assertThat(reloaded).containsEntry("source", "mainframe-batch").containsEntry("batchId", "B-9042");
    }

    @Test
    void listing_isChronological_regardlessOfInsertOrder() {
        // Inserted out of order on purpose.
        repository.save(event("evt-late", "acct-1", EventType.CREDIT, "10.00", Instant.parse("2026-05-15T12:00:00Z")));
        repository.save(event("evt-early", "acct-1", EventType.CREDIT, "10.00", Instant.parse("2026-05-15T08:00:00Z")));
        repository.save(event("evt-mid", "acct-1", EventType.CREDIT, "10.00", Instant.parse("2026-05-15T10:00:00Z")));
        // Different account must not leak into the listing.
        repository.save(event("evt-other", "acct-2", EventType.CREDIT, "10.00", Instant.parse("2026-05-15T09:00:00Z")));

        List<Event> events = repository.findByAccountIdOrderByEventTimestampAscEventIdAsc("acct-1");

        assertThat(events).extracting(Event::getEventId).containsExactly("evt-early", "evt-mid", "evt-late");
    }

    @Test
    void computeBalance_netsCreditsMinusDebits() {
        repository.save(event("evt-1", "acct-1", EventType.CREDIT, "150.00", Instant.parse("2026-05-15T10:00:00Z")));
        repository.save(event("evt-2", "acct-1", EventType.DEBIT, "40.50", Instant.parse("2026-05-15T11:00:00Z")));
        repository.save(event("evt-3", "acct-1", EventType.CREDIT, "0.50", Instant.parse("2026-05-15T09:00:00Z")));

        // 150.00 - 40.50 + 0.50 = 110.00
        assertThat(repository.computeBalance("acct-1")).isEqualByComparingTo("110.00");
    }

    @Test
    void computeBalance_unknownAccount_isZero() {
        assertThat(repository.computeBalance("never-seen")).isEqualByComparingTo("0");
    }

    @Test
    void uniqueConstraint_rejectsDuplicateEventId() {
        repository.saveAndFlush(event("evt-dup", "acct-1", EventType.CREDIT, "10.00", Instant.parse("2026-05-15T10:00:00Z")));

        assertThatThrownBy(() ->
                repository.saveAndFlush(event("evt-dup", "acct-1", EventType.DEBIT, "99.00", Instant.parse("2026-05-15T11:00:00Z")))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
