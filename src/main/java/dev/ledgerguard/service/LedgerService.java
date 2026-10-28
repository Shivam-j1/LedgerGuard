package dev.ledgerguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerguard.account.Account;
import dev.ledgerguard.account.AccountRepository;
import dev.ledgerguard.api.dto.AccountResponse;
import dev.ledgerguard.api.dto.AmountRequest;
import dev.ledgerguard.api.dto.CreateAccountRequest;
import dev.ledgerguard.api.dto.TransactionResponse;
import dev.ledgerguard.api.dto.TransferRequest;
import dev.ledgerguard.api.dto.TransferResponse;
import dev.ledgerguard.idempotency.IdempotencyRecord;
import dev.ledgerguard.idempotency.IdempotencyRecordRepository;
import dev.ledgerguard.ledger.LedgerEntry;
import dev.ledgerguard.ledger.LedgerEntryRepository;
import dev.ledgerguard.ledger.TransactionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final IdempotencyRecordRepository idempotency;
    private final ObjectMapper objectMapper;

    public LedgerService(AccountRepository accounts, LedgerEntryRepository entries,
                         IdempotencyRecordRepository idempotency, ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.entries = entries;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = accounts.saveAndFlush(new Account(request.ownerName().trim(), request.currency()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(long id) {
        return AccountResponse.from(accounts.findById(id).orElseThrow(() -> ApiException.notFound("Account", id)));
    }

    @Transactional
    public TransactionResponse deposit(long accountId, AmountRequest request, String key) {
        String hash = hash("deposit|" + accountId + "|" + canonical(request.amount()) + "|" + request.description());
        TransactionResponse replay = replay(key, hash, TransactionResponse.class);
        if (replay != null) return replay;

        Account account = lockAccount(accountId);
        account.credit(request.amount());
        UUID reference = UUID.randomUUID();
        LedgerEntry entry = entries.saveAndFlush(new LedgerEntry(account, TransactionType.DEPOSIT,
                request.amount(), reference, null, request.description()));
        TransactionResponse response = TransactionResponse.from(entry);
        saveIdempotency(key, hash, response);
        return response;
    }

    @Transactional
    public TransactionResponse withdraw(long accountId, AmountRequest request, String key) {
        String hash = hash("withdrawal|" + accountId + "|" + canonical(request.amount()) + "|" + request.description());
        TransactionResponse replay = replay(key, hash, TransactionResponse.class);
        if (replay != null) return replay;

        Account account = lockAccount(accountId);
        ensureFunds(account, request.amount());
        account.debit(request.amount());
        UUID reference = UUID.randomUUID();
        LedgerEntry entry = entries.saveAndFlush(new LedgerEntry(account, TransactionType.WITHDRAWAL,
                request.amount(), reference, null, request.description()));
        TransactionResponse response = TransactionResponse.from(entry);
        saveIdempotency(key, hash, response);
        return response;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request, String key) {
        String hash = hash("transfer|" + request.sourceAccountId() + "|" + request.destinationAccountId()
                + "|" + canonical(request.amount()) + "|" + request.description());
        TransferResponse replay = replay(key, hash, TransferResponse.class);
        if (replay != null) return replay;

        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT", "Source and destination accounts must differ");
        }

        long firstId = Math.min(request.sourceAccountId(), request.destinationAccountId());
        long secondId = Math.max(request.sourceAccountId(), request.destinationAccountId());
        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);
        Account source = first.getId().equals(request.sourceAccountId()) ? first : second;
        Account destination = first.getId().equals(request.destinationAccountId()) ? first : second;

        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH", "Accounts must use the same currency");
        }
        ensureFunds(source, request.amount());

        source.debit(request.amount());
        destination.credit(request.amount());
        UUID reference = UUID.randomUUID();
        entries.save(new LedgerEntry(source, TransactionType.TRANSFER_OUT, request.amount(), reference,
                destination.getId(), request.description()));
        entries.save(new LedgerEntry(destination, TransactionType.TRANSFER_IN, request.amount(), reference,
                source.getId(), request.description()));
        accounts.flush();
        entries.flush();

        TransferResponse response = new TransferResponse(reference, source.getId(), destination.getId(),
                request.amount(), source.getCurrency(), source.getBalance(), destination.getBalance());
        saveIdempotency(key, hash, response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(long accountId, int page, int size) {
        if (!accounts.existsById(accountId)) throw ApiException.notFound("Account", accountId);
        return entries.findByAccount_IdOrderByCreatedAtDescIdDesc(accountId,
                        PageRequest.of(page, size, Sort.unsorted()))
                .map(TransactionResponse::from);
    }

    private Account lockAccount(long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Account", id));
    }

    private void ensureFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Account balance is insufficient");
        }
    }

    private <T> T replay(String key, String requestHash, Class<T> responseType) {
        return idempotency.findByKey(key).map(record -> {
            if (!MessageDigest.isEqual(record.getRequestHash().getBytes(StandardCharsets.UTF_8),
                    requestHash.getBytes(StandardCharsets.UTF_8))) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This idempotency key was already used for a different request");
            }
            try {
                return objectMapper.readValue(record.getResponseJson(), responseType);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored idempotency response could not be read", e);
            }
        }).orElse(null);
    }

    private void saveIdempotency(String key, String requestHash, Object response) {
        try {
            idempotency.saveAndFlush(new IdempotencyRecord(key, requestHash, objectMapper.writeValueAsString(response)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Transaction response could not be stored", e);
        }
    }

    private String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String canonical(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
