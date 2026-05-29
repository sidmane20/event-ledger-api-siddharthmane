package com.eventledger.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a single event lookup finds no matching {@code eventId}.
 *
 * <p>The {@link ResponseStatus} annotation maps it to {@code 404 Not Found}. A later step refines
 * the response into an RFC 7807 problem document via a central exception handler.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
    }
}
