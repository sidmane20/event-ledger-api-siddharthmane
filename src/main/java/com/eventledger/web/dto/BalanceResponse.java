package com.eventledger.web.dto;

import java.math.BigDecimal;

/**
 * Response for {@code GET /accounts/{accountId}/balance}.
 *
 * <p>{@code balance} is the net of all credits minus all debits for the account, computed from the
 * full event history and therefore independent of the order events arrived in.
 */
public record BalanceResponse(String accountId, BigDecimal balance) {
}
