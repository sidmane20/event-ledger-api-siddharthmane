package com.eventledger.web.error;

/**
 * Thrown when a single event lookup finds no matching {@code eventId}.
 *
 * <p>{@link GlobalExceptionHandler} maps this to a {@code 404 Not Found} RFC 7807 response, keeping
 * the HTTP mapping in one place.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
    }
}
