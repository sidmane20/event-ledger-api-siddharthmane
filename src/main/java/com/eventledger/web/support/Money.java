package com.eventledger.web.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for presenting monetary values on the API boundary.
 *
 * <p>Amounts are validated to at most two decimal places on input, so normalising to a fixed scale
 * of {@code 2} here only pads trailing zeros (e.g. {@code 150} &rarr; {@code 150.00}); it never
 * rounds away real precision. This keeps every money value in the API rendered consistently
 * (e.g. {@code 150.00}, {@code 40.50}, {@code 109.50}) regardless of how the database returns it.
 */
public final class Money {

    /** Number of decimal places used to present monetary values. */
    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
