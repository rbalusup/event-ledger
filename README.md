# Event Ledger

Two independent Spring Boot microservices that process financial transaction events: an
**Event Gateway** (public-facing) and an **Account Service** (internal, called only by the
Gateway).

## Architecture

```
                       ┌──────────────────────┐
Client ───────────────▶│  Event Gateway API    │  :8080
                       │  (public-facing)      │
                       └──────────┬────────────┘
                                  │ REST (sync, via RestTemplate)
                                  ▼
                       ┌──────────────────────┐
                       │  Account Service      │  :8081
                       │  (internal)           │
                       └──────────────────────┘
```

- **Event Gateway** (`event-gateway/`) receives transaction events, validates input,
  enforces idempotency by `eventId`, stores its own event records in an H2 in-memory
  database, and calls the Account Service to apply the transaction. It wraps that call
  in a Resilience4j circuit breaker so a failing Account Service doesn't hang or take
  down the Gateway.
- **Account Service** (`account-service/`) owns account balances and transaction history
  in its own H2 in-memory database. It is never exposed to external clients directly —
  only the Gateway calls it.

The two services share no database and no in-process state. They are independent Gradle
projects, each with its own Gradle wrapper, and can be built and run entirely on their own.

## Prerequisites

- Java 21
- Docker + Docker Compose (for the primary way of running both services) — no local
  Gradle install needed either way, since both projects use the Gradle wrapper.

## Running via Docker Compose (recommended)

```bash
docker compose up --build
```

This starts both services with health checks; the Gateway waits for the Account
Service to report healthy before starting. Once up:

```bash
# Submit a transaction event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z"
  }'

# Get a single event by id
curl http://localhost:8080/events/evt-001

# List events for an account, in chronological order
curl "http://localhost:8080/events?account=acct-123"

# Gateway health check
curl http://localhost:8080/health

# Account balance (served directly by the Account Service)
curl http://localhost:8081/accounts/acct-123/balance

# Account details + recent transactions
curl http://localhost:8081/accounts/acct-123

# Account Service health check
curl http://localhost:8081/health
```

Resubmitting the same `eventId` returns the original event with `"duplicate": true` and
`200 OK` instead of creating a second record or changing the balance.

## Running manually (without Docker)

In one terminal:

```bash
cd account-service
./gradlew bootRun          # listens on :8081
```

In another terminal:

```bash
cd event-gateway
./gradlew bootRun          # listens on :8080, calls http://localhost:8081 by default
```

To point the Gateway at an Account Service running somewhere else, set
`ACCOUNT_SERVICE_URL` (e.g. `ACCOUNT_SERVICE_URL=http://localhost:9090 ./gradlew bootRun`).

## Running the tests

```bash
cd account-service && ./gradlew test
cd event-gateway && ./gradlew test
```

| Test class | Covers |
|---|---|
| `account-service` `AccountServiceTest` | Balance computation (CREDIT − DEBIT), order-independent balance and transaction listing regardless of arrival order, idempotent apply-transaction by `eventId`, 404 on unknown account |
| `account-service` `AccountControllerValidationTest` | 400 on missing/invalid fields, 404 mapping |
| `account-service` `TraceIdFilterTest` | Trace ID is generated when absent, preserved unchanged when supplied |
| `event-gateway` `EventServiceTest` | Idempotent event storage, chronological listing regardless of arrival order, metadata round-trip, **no local persistence when the Account Service call fails** |
| `event-gateway` `EventControllerValidationTest` | 400 on missing/invalid fields, 404 mapping |
| `event-gateway` `AccountServiceClientCircuitBreakerTest` | Circuit breaker opens after repeated failures and fails fast (no calls reach the downstream while open), then recovers to closed once calls succeed again |
| `event-gateway` `TraceIdPropagationTest` | `X-Trace-Id` is generated or passed through, and forwarded to the Account Service on the outbound call |
| `event-gateway` `EventGatewayEndToEndIT` | Full real-HTTP flow: submit → balance updated on a real Account Service instance → resubmit is idempotent |

## API reference

### Event Gateway (`:8080`)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/events` | `201` on new event, `200` + `"duplicate": true` on resubmission, `400` on invalid input, `503` if the Account Service is unavailable |
| `GET` | `/events/{id}` | `200`, or `404` if not found |
| `GET` | `/events?account={accountId}` | Events for the account, ordered by `eventTimestamp` ascending |
| `GET` | `/health` | Service status + H2 connectivity |

### Account Service (`:8081`)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | `201` on new transaction, `200` + `"alreadyApplied": true` on resubmission of the same `eventId` |
| `GET` | `/accounts/{accountId}/balance` | `200`, or `404` if the account has no transactions |
| `GET` | `/accounts/{accountId}` | Balance + full transaction history, ordered by `eventTimestamp` ascending |
| `GET` | `/health` | Service status + H2 connectivity |

## Resiliency: why a circuit breaker

The Gateway wraps its call to the Account Service in a Resilience4j circuit breaker
(`event-gateway/src/main/resources/application.yml`, instance `accountService`):
count-based sliding window of 10 calls, minimum 5 calls before evaluating, 50% failure
threshold, 10s wait in the open state, 3 trial calls in half-open.

A circuit breaker was chosen over plain retry-with-backoff because the failure mode this
system most needs to guard against is a **sustained** Account Service outage, not an
occasional blip. Retrying during a real outage just adds load and delay for every caller;
failing fast after a threshold of failures gives clients an immediate, honest `503`
instead of a slow one, and stops hammering a downstream that's already struggling. The
Gateway's `RestTemplate` also has explicit 2s connect/read timeouts — a circuit breaker
alone doesn't help if the underlying HTTP client call is left free to hang.

## Tracing & observability: why manual trace IDs over full OpenTelemetry

Both services generate or accept an `X-Trace-Id` header per request (a servlet filter,
`TraceIdFilter`), store it in SLF4J's MDC for the lifetime of the request, log a
received/completed line carrying it, and echo it back on the response. The Gateway's
`RestTemplate` has an interceptor that forwards the same trace ID on its call to the
Account Service, so a single client request is traceable end-to-end across both
services' logs by grepping for one ID.

This is deliberately lighter than the full OpenTelemetry SDK: at two services and no
collector/exporter infrastructure to stand up, a manual header + MDC gives the same
practical traceability the assignment asks for (generate → propagate → log on both
sides) without the setup and dependency surface of a full tracing stack.

Both services log structured JSON (via `logstash-logback-encoder`): `@timestamp`,
`level`, `message`, `service`, and `traceId` (via MDC) on every line. Each service also
exposes one custom Micrometer counter — `gateway.events.submitted` and
`account.transactions.applied`, both tagged by outcome — via
`/actuator/metrics/{name}`.

## Known limitations / out of scope

- No Prometheus/Grafana, Jaeger/Zipkin, rate limiting, async fallback queueing, or
  contract tests — these were explicitly listed as bonus/optional in the assignment and
  were left out to keep the core solid within the time budget.
- Balance/currency handling assumes a single currency per account (taken from the first
  transaction); the assignment's payload doesn't describe multi-currency accounts.
- The Gateway persists its own `Event` record only *after* the Account Service call
  succeeds, so a failed call never leaves the Gateway in an inconsistent state — but if
  the Account Service applies a transaction and the response is then lost to a network
  blip, a client retry with the same `eventId` is what makes the system consistent again
  (the Account Service's `event_id` unique constraint makes that retry a safe no-op).
  This is "safe to retry," not a distributed transaction — there's no two-phase commit
  between the two services' databases.
