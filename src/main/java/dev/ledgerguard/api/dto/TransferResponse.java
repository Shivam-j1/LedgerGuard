package dev.ledgerguard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(UUID referenceId, Long sourceAccountId, Long destinationAccountId,
                               BigDecimal amount, String currency, BigDecimal sourceBalance,
                               BigDecimal destinationBalance) { }
