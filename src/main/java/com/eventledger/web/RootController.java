package com.eventledger.web;

import com.eventledger.web.dto.ApiInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves a service-discovery document at the API root so {@code GET /} returns a helpful 200
 * (name, version, status, and links) rather than a bare 404.
 */
@RestController
@Tag(name = "Service", description = "Service information")
public class RootController {

    @Operation(summary = "API root", description = "Service info, status and links to docs/health.")
    @GetMapping("/")
    public ApiInfoResponse root() {
        return ApiInfoResponse.current();
    }
}
