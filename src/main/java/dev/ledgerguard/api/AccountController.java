package dev.ledgerguard.api;

import dev.ledgerguard.api.dto.AccountResponse;
import dev.ledgerguard.api.dto.CreateAccountRequest;
import dev.ledgerguard.api.dto.TransactionResponse;
import dev.ledgerguard.service.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final LedgerService ledgerService;

    public AccountController(LedgerService ledgerService) { this.ledgerService = ledgerService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return ledgerService.createAccount(request);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@PathVariable long accountId) { return ledgerService.getAccount(accountId); }

    @GetMapping("/{accountId}/transactions")
    public Page<TransactionResponse> history(@PathVariable long accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ledgerService.history(accountId, page, size);
    }

    @PostMapping("/{accountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@PathVariable long accountId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
            @Valid @RequestBody dev.ledgerguard.api.dto.AmountRequest request) {
        return ledgerService.deposit(accountId, request, key);
    }

    @PostMapping("/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@PathVariable long accountId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
            @Valid @RequestBody dev.ledgerguard.api.dto.AmountRequest request) {
        return ledgerService.withdraw(accountId, request, key);
    }
}
