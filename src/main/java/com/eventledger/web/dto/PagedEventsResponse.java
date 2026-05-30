package com.eventledger.web.dto;

import com.eventledger.domain.Event;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of events plus the metadata a client needs to navigate the rest.
 *
 * <p>Used by the listing endpoint so large accounts can be read incrementally without changing the
 * chronological ordering of the underlying events.
 */
public record PagedEventsResponse(
        List<EventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static PagedEventsResponse from(Page<Event> page) {
        List<EventResponse> content = page.getContent().stream()
                .map(EventResponse::from)
                .toList();
        return new PagedEventsResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
