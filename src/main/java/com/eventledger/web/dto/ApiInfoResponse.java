package com.eventledger.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small "service discovery" document served at the API root ({@code GET /}).
 *
 * <p>Returning a structured info document (name, version, status, and links to docs/health and the
 * available endpoints) is the conventional, production-friendly alternative to either a bare 404 or
 * a plain "hello" string at the root.
 */
public record ApiInfoResponse(
        String name,
        String version,
        String status,
        Map<String, String> documentation,
        Map<String, String> endpoints
) {

    public static ApiInfoResponse current() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("swaggerUi", "/swagger-ui.html");
        docs.put("openApi", "/v3/api-docs");
        docs.put("health", "/actuator/health");

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("submitEvent", "POST /events");
        endpoints.put("getEvent", "GET /events/{id}");
        endpoints.put("listEvents", "GET /events?account={accountId}");
        endpoints.put("accountBalance", "GET /accounts/{accountId}/balance");

        return new ApiInfoResponse("Event Ledger API", "v1", "UP", docs, endpoints);
    }
}
