package dev.ledgerguard.ledger;

import dev.ledgerguard.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reference_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID referenceId;

    @Column(name = "counterparty_account_id")
    private Long counterpartyAccountId;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() { }

    public LedgerEntry(Account account, TransactionType type, BigDecimal amount,
                       UUID referenceId, Long counterpartyAccountId, String description) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.referenceId = referenceId;
        this.counterpartyAccountId = counterpartyAccountId;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return account.getId(); }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public UUID getReferenceId() { return referenceId; }
    public Long getCounterpartyAccountId() { return counterpartyAccountId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
