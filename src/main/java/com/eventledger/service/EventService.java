package com.eventledger.service;

import com.eventledger.domain.Event;
import com.eventledger.repository.EventRepository;
import com.eventledger.web.dto.CreateEventRequest;
import com.eventledger.web.error.EventNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Application service for ingesting and recording ledger events.
 */
@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotently records an event, safely even under concurrent submissions of the same id.
     *
     * <p>The fast path returns the original event if one already exists, so re-delivery never
     * duplicates a row or moves the balance. When two requests for the same {@code eventId} race
     * past that check at the same time, both attempt the insert; the database's unique constraint
     * lets exactly one win, and the loser's {@link DataIntegrityViolationException} is translated
     * into a normal "already exists" outcome by re-reading the winning row. The net effect is that
     * simultaneous POSTs behave identically to sequential duplicates.
     *
     * <p>{@code saveAndFlush} forces the insert (and thus the constraint check) to happen here,
     * inside this method's own transaction, rather than at a later commit where it could not be
     * handled.
     *
     * @return the stored event together with whether this call created it
     */
    public SubmissionResult submit(CreateEventRequest request) {
        Optional<Event> existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return new SubmissionResult(existing.get(), false);
        }
        try {
            return new SubmissionResult(repository.saveAndFlush(toEntity(request)), true);
        } catch (DataIntegrityViolationException race) {
            // A concurrent request inserted the same eventId between our check and our insert.
            Event winner = repository.findByEventId(request.eventId()).orElseThrow(() -> race);
            return new SubmissionResult(winner, false);
        }
    }

    /**
     * Retrieves a single event by its {@code eventId}.
     *
     * @throws EventNotFoundException if no event with that id exists
     */
    @Transactional(readOnly = true)
    public Event getByEventId(String eventId) {
        return repository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /**
     * Lists events for an account, one page at a time, in chronological order by
     * {@code eventTimestamp} (tie-broken by {@code eventId}) regardless of arrival order. The sort
     * is fixed here so a client cannot weaken the ordering guarantee. An account with no events
     * yields an empty page.
     */
    @Transactional(readOnly = true)
    public Page<Event> listByAccount(String accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("eventTimestamp"), Sort.Order.asc("eventId")));
        return repository.findByAccountId(accountId, pageable);
    }

    /**
     * Computes the net balance for an account: {@code sum(CREDIT) - sum(DEBIT)}. An account with no
     * events returns {@code 0}. Correct regardless of the order events arrived in.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountId) {
        return repository.computeBalance(accountId);
    }

    private static Event toEntity(CreateEventRequest request) {
        return new Event(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                request.metadata()
        );
    }

    /**
     * Outcome of a submission: the canonical stored event and whether it was newly created
     * ({@code true}) versus returned as an existing duplicate ({@code false}).
     */
    public record SubmissionResult(Event event, boolean created) {
    }
}
