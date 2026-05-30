package com.eventledger.service;

import com.eventledger.domain.Event;
import com.eventledger.repository.EventRepository;
import com.eventledger.web.dto.CreateEventRequest;
import com.eventledger.web.error.EventNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
     * Idempotently records an event.
     *
     * <p>If an event with the same {@code eventId} already exists, the original is returned
     * unchanged and nothing is written &mdash; so re-delivery of the same event never duplicates a
     * row or moves the balance. Otherwise the event is persisted as new.
     *
     * @return the stored event together with whether this call created it
     */
    @Transactional
    public SubmissionResult submit(CreateEventRequest request) {
        return repository.findByEventId(request.eventId())
                .map(existing -> new SubmissionResult(existing, false))
                .orElseGet(() -> new SubmissionResult(repository.save(toEntity(request)), true));
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
