package com.eventledger.web;

import com.eventledger.service.EventService;
import com.eventledger.service.EventService.SubmissionResult;
import com.eventledger.web.dto.CreateEventRequest;
import com.eventledger.web.dto.EventResponse;
import com.eventledger.web.dto.PagedEventsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST endpoints for submitting and retrieving transaction events.
 */
@RestController
@RequestMapping("/events")
@Validated
@Tag(name = "Events", description = "Submit and retrieve transaction events")
public class EventController {

    /** Upper bound on page size, to keep responses bounded. */
    private static final int MAX_PAGE_SIZE = 200;

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Submit a transaction event.
     *
     * <p>Idempotent: a brand-new event yields {@code 201 Created}; re-submitting an existing
     * {@code eventId} yields {@code 200 OK} with the original event (no duplicate, balance
     * unchanged). Both responses carry a {@code Location} header pointing at the canonical event.
     */
    @Operation(summary = "Submit a transaction event",
            description = "Idempotent: a new eventId returns 201; a repeat returns 200 with the original event.")
    @PostMapping
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody CreateEventRequest request) {
        SubmissionResult result = eventService.submit(request);
        EventResponse body = EventResponse.from(result.event());
        URI location = URI.create("/events/" + body.eventId());
        return result.created()
                ? ResponseEntity.created(location).body(body)
                : ResponseEntity.ok().location(location).body(body);
    }

    /**
     * Retrieve a single event by its id. Returns {@code 404 Not Found} if it does not exist.
     */
    @Operation(summary = "Get a single event by id", description = "Returns 404 if no event has the id.")
    @GetMapping("/{eventId}")
    public EventResponse getById(@PathVariable String eventId) {
        return EventResponse.from(eventService.getByEventId(eventId));
    }

    /**
     * List events for an account, always ordered chronologically by {@code eventTimestamp}
     * regardless of arrival order. Results are paginated via {@code page} (0-based) and {@code size};
     * an unknown account yields an empty page (not a 404).
     */
    @Operation(summary = "List an account's events (paginated)",
            description = "Always chronological by eventTimestamp; supports page/size.")
    @GetMapping
    public PagedEventsResponse listByAccount(
            @RequestParam("account") String accountId,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return PagedEventsResponse.from(eventService.listByAccount(accountId, page, size));
    }
}
