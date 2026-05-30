package com.eventledger.repository;

import com.eventledger.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Persistence gateway for {@link Event}s.
 *
 * <p>The derived and explicit queries here encode the API's core guarantees:
 * <ul>
 *   <li>{@link #findByEventId} powers idempotent submission and single-event retrieval.</li>
 *   <li>{@link #findByAccountIdOrderByEventTimestampAscEventIdAsc} returns events in chronological
 *       order regardless of arrival order, tie-broken by {@code eventId} for determinism.</li>
 *   <li>{@link #computeBalance} nets credits against debits with a single aggregate, so the result
 *       is correct independent of insertion order.</li>
 * </ul>
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<Event> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId);

    /**
     * Paginated listing for an account. Ordering is supplied by the {@link Pageable}'s {@code Sort}
     * (fixed to {@code eventTimestamp, eventId} by the service) so chronological order is preserved
     * across pages regardless of arrival order.
     */
    Page<Event> findByAccountId(String accountId, Pageable pageable);

    /**
     * Net balance for an account: {@code sum(CREDIT) - sum(DEBIT)}.
     *
     * <p>{@code coalesce(..., 0)} ensures an account with no events yields {@code 0} rather than
     * {@code null}.
     */
    @Query("""
            select coalesce(sum(
                       case when e.type = com.eventledger.domain.EventType.CREDIT
                            then e.amount
                            else e.amount * -1
                       end), 0)
            from Event e
            where e.accountId = :accountId
            """)
    BigDecimal computeBalance(@Param("accountId") String accountId);
}
