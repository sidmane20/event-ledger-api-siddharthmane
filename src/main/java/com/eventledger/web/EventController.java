package com.eventledger.web;

import com.eventledger.service.EventService;
import com.eventledger.service.EventService.SubmissionResult;
import com.eventledger.web.dto.CreateEventRequest;
import com.eventledger.web.dto.EventResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST endpoints for submitting and retrieving transaction events.
 */
@RestController
@RequestMapping("/events")
public class EventController {

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
    @PostMapping
    public ResponseEntity<EventResponse> submit(@RequestBody CreateEventRequest request) {
        SubmissionResult result = eventService.submit(request);
        EventResponse body = EventResponse.from(result.event());
        URI location = URI.create("/events/" + body.eventId());
        return result.created()
                ? ResponseEntity.created(location).body(body)
                : ResponseEntity.ok().location(location).body(body);
    }
}
