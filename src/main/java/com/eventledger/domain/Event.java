package com.eventledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * A single, immutable financial transaction event recorded in the ledger.
 *
 * <p>Design notes that support the API's correctness guarantees:
 * <ul>
 *   <li>A surrogate {@code id} is the primary key, while the upstream-supplied {@code eventId}
 *       carries a <strong>unique constraint</strong>. That constraint is the ultimate guard for
 *       idempotency &mdash; even under concurrent submissions the database admits at most one row
 *       per {@code eventId}.</li>
 *   <li>{@code amount} is a {@link BigDecimal} (never a floating-point type) so money is exact.</li>
 *   <li>A composite index on {@code (accountId, eventTimestamp)} backs the chronological listing
 *       query, which is independent of the order events actually arrived in.</li>
 *   <li>{@code receivedAt} records server-side arrival time for auditing; it is distinct from
 *       {@code eventTimestamp}, which is when the event originally occurred upstream.</li>
 * </ul>
 */
@Entity
@Table(
        name = "events",
        uniqueConstraints = @UniqueConstraint(name = "uk_events_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_events_account_timestamp", columnList = "account_id, event_timestamp")
)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16, updatable = false)
    private EventType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    @Convert(converter = MetadataConverter.class)
    @Column(name = "metadata", columnDefinition = "TEXT", updatable = false)
    private Map<String, Object> metadata;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /** Required by JPA. */
    protected Event() {
    }

    public Event(String eventId,
                 String accountId,
                 EventType type,
                 BigDecimal amount,
                 String currency,
                 Instant eventTimestamp,
                 Map<String, Object> metadata) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.metadata = metadata;
    }

    @PrePersist
    void onPersist() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public EventType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
