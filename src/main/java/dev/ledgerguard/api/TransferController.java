package dev.ledgerguard.api;

import dev.ledgerguard.api.dto.TransferRequest;
import dev.ledgerguard.api.dto.TransferResponse;
import dev.ledgerguard.service.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final LedgerService ledgerService;

    public TransferController(LedgerService ledgerService) { this.ledgerService = ledgerService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
            @Valid @RequestBody TransferRequest request) {
        return ledgerService.transfer(request, key);
    }
}
