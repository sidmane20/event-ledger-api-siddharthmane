package com.eventledger.web;

import com.eventledger.service.EventService;
import com.eventledger.web.dto.BalanceResponse;
import com.eventledger.web.support.Money;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for account-level views derived from the event ledger.
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final EventService eventService;

    public AccountController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Current computed balance for an account: net of credits minus debits. An account with no
     * events reports a balance of {@code 0}.
     */
    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return new BalanceResponse(accountId, Money.scale(eventService.getBalance(accountId)));
    }
}
