package dev.ledgerguard.api.dto;

import dev.ledgerguard.ledger.LedgerEntry;
import dev.ledgerguard.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(Long id, Long accountId, TransactionType type, BigDecimal amount,
                                  BigDecimal balanceAfter, UUID referenceId, Long counterpartyAccountId,
                                  String description, Instant createdAt) {
    public static TransactionResponse from(LedgerEntry entry) {
        return new TransactionResponse(entry.getId(), entry.getAccountId(), entry.getType(), entry.getAmount(),
                entry.getBalanceAfter(), entry.getReferenceId(), entry.getCounterpartyAccountId(),
                entry.getDescription(), entry.getCreatedAt());
    }
}
