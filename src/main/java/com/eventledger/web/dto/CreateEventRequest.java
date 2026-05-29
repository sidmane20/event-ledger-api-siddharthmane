package com.eventledger.web.dto;

import com.eventledger.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Inbound payload for {@code POST /events}.
 *
 * <p>Kept separate from the {@link com.eventledger.domain.Event} entity so the wire contract and
 * the persistence model can evolve independently. Bean Validation constraints are added in a later
 * step; here the type simply models the request shape.
 */
public record CreateEventRequest(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata
) {
}
