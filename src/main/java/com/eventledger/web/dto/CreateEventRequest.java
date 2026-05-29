package com.eventledger.web.dto;

import com.eventledger.domain.EventType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Inbound payload for {@code POST /events}.
 *
 * <p>Kept separate from the {@link com.eventledger.domain.Event} entity so the wire contract and
 * the persistence model can evolve independently. Bean Validation constraints reject malformed
 * submissions at the boundary so they never reach the service or database:
 * <ul>
 *   <li>required string fields must be present and non-blank;</li>
 *   <li>{@code amount} must be strictly greater than zero;</li>
 *   <li>{@code type} must be present and is bound to the {@link EventType} enum, so any value other
 *       than {@code CREDIT}/{@code DEBIT} is rejected during deserialization.</li>
 * </ul>
 */
public record CreateEventRequest(

        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "accountId is required")
        String accountId,

        @NotNull(message = "type is required and must be CREDIT or DEBIT")
        EventType type,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "amount supports at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp,

        Map<String, Object> metadata
) {
}
