package dev.ledgerguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerguard.account.Account;
import dev.ledgerguard.account.AccountRepository;
import dev.ledgerguard.api.dto.AmountRequest;
import dev.ledgerguard.api.dto.TransferRequest;
import dev.ledgerguard.api.dto.TransferResponse;
import dev.ledgerguard.idempotency.IdempotencyRecord;
import dev.ledgerguard.idempotency.IdempotencyRecordRepository;
import dev.ledgerguard.ledger.LedgerEntry;
import dev.ledgerguard.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock AccountRepository accounts;
    @Mock LedgerEntryRepository entries;
    @Mock IdempotencyRecordRepository idempotency;

    LedgerService service;

    @BeforeEach
    void setUp() {
        service = new LedgerService(accounts, entries, idempotency, new ObjectMapper());
        when(entries.save(any(LedgerEntry.class))).thenAnswer(call -> call.getArgument(0));
        when(entries.saveAndFlush(any(LedgerEntry.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void transferDebitsAndCreditsAsOneOperationAndWritesPairedEntries() {
        Account source = account(10L, "1000.00");
        Account destination = account(20L, "50.00");
        when(accounts.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(20L)).thenReturn(Optional.of(destination));
        when(idempotency.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(call -> call.getArgument(0));

        TransferResponse response = service.transfer(new TransferRequest(20L, 10L,
                new BigDecimal("125.50"), "test transfer"), "transfer-001");

        assertEquals(new BigDecimal("874.50"), source.getBalance());
        assertEquals(new BigDecimal("175.50"), destination.getBalance());
        assertEquals(new BigDecimal("874.50"), response.sourceBalance());
        assertEquals(new BigDecimal("175.50"), response.destinationBalance());
        assertNotNull(response.referenceId());
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entries, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(captor.getAllValues().get(0).getReferenceId(), captor.getAllValues().get(1).getReferenceId());
        verify(accounts).flush();
    }

    @Test
    void withdrawalWithInsufficientFundsDoesNotWriteLedgerOrIdempotency() {
        Account account = account(1L, "20.00");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        ApiException error = assertThrows(ApiException.class,
                () -> service.withdraw(1L, new AmountRequest(new BigDecimal("21.00"), "too much"), "withdraw-001"));

        assertEquals("INSUFFICIENT_FUNDS", error.getCode());
        assertEquals(new BigDecimal("20.00"), account.getBalance());
        verify(entries, never()).save(any());
        verify(idempotency, never()).saveAndFlush(any());
    }

    @Test
    void failedTransferLeavesBothBalancesAndLedgerUnchanged() {
        Account source = account(10L, "10.00");
        Account destination = account(20L, "40.00");
        when(accounts.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(20L)).thenReturn(Optional.of(destination));

        ApiException error = assertThrows(ApiException.class, () -> service.transfer(
                new TransferRequest(10L, 20L, new BigDecimal("11.00"), "too much"), "transfer-fail-001"));

        assertEquals("INSUFFICIENT_FUNDS", error.getCode());
        assertEquals(new BigDecimal("10.00"), source.getBalance());
        assertEquals(new BigDecimal("40.00"), destination.getBalance());
        verify(entries, never()).save(any());
        verify(idempotency, never()).saveAndFlush(any());
    }

    @Test
    void reusingKeyWithDifferentAmountIsRejected() {
        when(idempotency.findByKey("deposit-001")).thenReturn(Optional.of(
                new IdempotencyRecord("deposit-001", "a-different-hash", "{}")));

        ApiException error = assertThrows(ApiException.class, () -> service.deposit(1L,
                new AmountRequest(new BigDecimal("10.00"), "same key"), "deposit-001"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getCode());
        verify(accounts, never()).findByIdForUpdate(1L);
    }

    @Test
    void completedDepositWithSameKeyAndBodyReturnsStoredResponse() {
        Account account = account(1L, "0.00");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        Map<String, IdempotencyRecord> saved = new java.util.HashMap<>();
        doAnswer(call -> {
            IdempotencyRecord record = call.getArgument(0);
            saved.put("deposit-001", record);
            return record;
        }).when(idempotency).saveAndFlush(any(IdempotencyRecord.class));
        when(idempotency.findByKey("deposit-001")).thenAnswer(call -> Optional.ofNullable(saved.get("deposit-001")));

        var first = service.deposit(1L, new AmountRequest(new BigDecimal("10.00"), "initial"), "deposit-001");
        var replay = service.deposit(1L, new AmountRequest(new BigDecimal("10.00"), "initial"), "deposit-001");

        assertEquals(first, replay);
        assertEquals(new BigDecimal("10.00"), account.getBalance());
        verify(accounts).findByIdForUpdate(1L);
        verify(entries).saveAndFlush(any(LedgerEntry.class));
    }

    private Account account(long id, String balance) {
        Account account = new Account("Test Owner", "USD");
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }
}
