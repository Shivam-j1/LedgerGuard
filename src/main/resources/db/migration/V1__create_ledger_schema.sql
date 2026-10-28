CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_name VARCHAR(120) NOT NULL,
    currency CHAR(3) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_accounts_balance_nonnegative CHECK (balance >= 0)
);

CREATE TABLE ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    reference_id CHAR(36) NOT NULL,
    counterparty_account_id BIGINT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0),
    INDEX idx_ledger_account_created (account_id, created_at, id),
    INDEX idx_ledger_reference (reference_id)
);

CREATE TABLE idempotency_records (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);
