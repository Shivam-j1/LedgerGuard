# LedgerGuard

LedgerGuard is a Spring Boot REST API for account balances and a transaction ledger. It demonstrates transactional money movement, request validation, deterministic row locking, and idempotent writes.

## Features

- Create accounts with a three-letter currency code.
- Deposit, withdraw, and transfer money between accounts.
- Reject overdrafts and transfers between different currencies.
- Store each balance change as an append-only ledger entry.
- Use database transactions so a transfer's debit, credit, and ledger rows commit or roll back together.
- Require an `Idempotency-Key` for every money movement. Repeating a completed request with the same key and body returns the original response; reusing a key with another body returns `409`.
- Lock transfer accounts in ascending ID order to reduce deadlock risk.
- Browse paginated account transaction history.
- Bootstrap MySQL schema with Flyway; run with Docker Compose.

## Stack

Java 17, Spring Boot 3.5.16, Spring Data JPA, MySQL 8.4, Flyway, Maven, JUnit 5, Mockito.

## Run locally

Requirements: JDK 17+, Maven 3.6.3+, and Docker Compose.

```bash
docker compose up --build
```

The API listens on `http://localhost:8080`. MySQL is exposed on port `3306`. The compose credentials are for local development only; use secrets and a restricted database account outside a local demo.

To stop the services and remove the local database volume:

```bash
docker compose down -v
```

To run the unit tests:

```bash
mvn test
```

## API examples

### Create two accounts

```bash
curl -s http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName":"Aarav Mehta","currency":"INR"}'

curl -s http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName":"Isha Rao","currency":"INR"}'
```

Use the returned account IDs below.

### Deposit

```bash
curl -i http://localhost:8080/api/v1/accounts/1/deposits \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-account-1-001' \
  -d '{"amount":5000.00,"description":"Opening deposit"}'
```

### Transfer

```bash
curl -i http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: transfer-1-to-2-001' \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":1250.00,"description":"Shared expenses"}'
```

Retry that exact request with the same key to receive the original result. A retry with a changed amount or account IDs receives `409 Conflict`.

### Withdraw and inspect history

```bash
curl -i http://localhost:8080/api/v1/accounts/1/withdrawals \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: withdrawal-account-1-001' \
  -d '{"amount":250.00,"description":"Cash withdrawal"}'

curl -s 'http://localhost:8080/api/v1/accounts/1/transactions?page=0&size=20'
```

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/accounts` | Create an account |
| `GET` | `/api/v1/accounts/{accountId}` | Read account and current balance |
| `POST` | `/api/v1/accounts/{accountId}/deposits` | Deposit (requires idempotency key) |
| `POST` | `/api/v1/accounts/{accountId}/withdrawals` | Withdraw (requires idempotency key) |
| `POST` | `/api/v1/transfers` | Transfer between accounts (requires idempotency key) |
| `GET` | `/api/v1/accounts/{accountId}/transactions?page=0&size=20` | Paginated ledger history |
| `GET` | `/actuator/health` | Health check |

Money values use `DECIMAL(19,2)`. Requests with more than two fractional digits are rejected. A transfer creates paired `TRANSFER_OUT` and `TRANSFER_IN` ledger entries sharing one reference ID.

## Error behavior

Errors use a consistent JSON shape with `timestamp`, `status`, `code`, `message`, and optional `fields`. Common cases include `400` for invalid requests, `404` for unknown accounts, `409` for an idempotency-key conflict, and `422` for insufficient funds.

## Design notes

- `@Transactional` wraps each financial operation, including its ledger entries and idempotency record.
- `PESSIMISTIC_WRITE` locks serialize updates to the same account. Transfer locks are acquired by ascending account ID.
- The database enforces nonnegative balances and unique idempotency keys as a second line of defense.
- Idempotency records store a SHA-256 request fingerprint and the original response. A concurrent duplicate can receive `409` while the first request is committing; retry the same key and body after that response.
- This is a portfolio/demo service. It does not include authentication, authorization, multi-tenant isolation, external payment rails, or production secret management.

## Project layout

```text
src/main/java/dev/ledgerguard/
  account/       Account entity and repository
  api/           REST controllers, DTOs, and error mapping
  idempotency/   Idempotency persistence
  ledger/        Append-only ledger model and repository
  service/       Transactional business operations
src/main/resources/db/migration/  Flyway schema
src/test/java/                    Unit tests
```
