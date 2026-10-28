package dev.ledgerguard.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Page<LedgerEntry> findByAccount_IdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);
    List<LedgerEntry> findByReferenceIdOrderByAccount_IdAsc(UUID referenceId);
}
