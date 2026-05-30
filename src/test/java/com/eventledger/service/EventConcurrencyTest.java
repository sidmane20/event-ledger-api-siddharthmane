package com.eventledger.service;

import com.eventledger.domain.EventType;
import com.eventledger.repository.EventRepository;
import com.eventledger.web.dto.CreateEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventConcurrencyTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void simultaneousSubmissionsOfSameEventId_createExactlyOneEvent() throws Exception {
        int threadCount = 16;
        CreateEventRequest request = new CreateEventRequest(
                "evt-race", "acct-race", EventType.CREDIT, new BigDecimal("100.00"),
                "USD", Instant.parse("2026-05-15T10:00:00Z"), null);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Every thread blocks on the same starting gun so the submissions truly overlap.
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return eventService.submit(request).created();
            }));
        }

        ready.await();
        start.countDown();

        int createdCount = 0;
        AtomicInteger failures = new AtomicInteger();
        for (Future<Boolean> future : futures) {
            try {
                if (future.get()) {
                    createdCount++;
                }
            } catch (ExecutionException e) {
                failures.incrementAndGet();
            }
        }
        pool.shutdown();

        assertThat(failures.get()).as("no submission should fail under concurrency").isZero();
        assertThat(createdCount).as("exactly one submission creates the event").isEqualTo(1);
        assertThat(repository.count()).as("only one row is persisted").isEqualTo(1);
        assertThat(repository.computeBalance("acct-race"))
                .as("balance reflects a single credit, not duplicates")
                .isEqualByComparingTo("100.00");
    }
}
