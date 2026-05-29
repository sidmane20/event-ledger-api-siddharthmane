package com.eventledger.web.dto;

import com.eventledger.domain.Event;
import com.eventledger.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Outbound representation of a stored {@link Event}.
 *
 * <p>Exposes only the fields clients care about (notably hiding the internal surrogate {@code id})
 * and adds the server-assigned {@code receivedAt} audit timestamp.
 */
public record EventResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata,
        Instant receivedAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                event.getMetadata(),
                event.getReceivedAt()
        );
    }
}
