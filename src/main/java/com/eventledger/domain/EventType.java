package com.eventledger.domain;

/**
 * The kind of ledger movement an event represents.
 *
 * <p>A {@code CREDIT} increases an account balance; a {@code DEBIT} decreases it. Any other value
 * supplied by an upstream system is rejected at the API boundary (see request validation).
 */
public enum EventType {
    CREDIT,
    DEBIT
}
