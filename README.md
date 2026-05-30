# Event Ledger API

A small REST API that ingests financial transaction events from multiple upstream systems and
serves a per-account ledger. Upstream systems are not synchronised, so the API is built to handle
two realities head-on:

- **Out-of-order delivery** — an event with an earlier `eventTimestamp` may arrive after a later one.
- **Duplicate delivery** — the same event may be sent more than once.

The listing and balance views are always correct regardless of arrival order, and re-delivering an
event never creates a duplicate or moves the balance.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4 (Spring MVC) |
| Persistence | Spring Data JPA / Hibernate |
| Database | H2, in-memory (no external setup) |
| Build | Maven (via the bundled Maven Wrapper) |
| Tests | JUnit 5 + Spring `MockMvc` |

---

## Prerequisites

- **JDK 21 or newer** (`java -version` should report 21+).
- That's it — Maven does **not** need to be installed; the project ships the Maven Wrapper
  (`./mvnw`), and H2 is embedded, so there is no database to install or configure.

---

## Run the application

From the project root:

```bash
./mvnw spring-boot:run
```

The API starts on **http://localhost:8080**. Stop it with `Ctrl+C`.

> On Windows use `mvnw.cmd spring-boot:run`.

### Or run with Docker

A multi-stage `Dockerfile` and `docker-compose.yml` are included. With Docker installed:

```bash
docker compose up --build
```

This builds the jar and starts the API on **http://localhost:8080** — no JDK or Maven needed on the
host, and (because the database is embedded) no database container. Stop with `Ctrl+C`.

The in-memory database is recreated on every start, so the app always boots with an empty ledger.
An H2 web console is available at http://localhost:8080/h2-console (JDBC URL
`jdbc:h2:mem:eventledger`, user `sa`, no password) if you want to inspect the data.

---

## Run the tests

```bash
./mvnw test
```

This is a standard Maven project, so if you have Maven installed you can equivalently run:

```bash
mvn test
```

The suite covers every required behaviour: idempotent duplicate submissions, out-of-order arrival,
balance accuracy, and input validation / error cases.

---

## Interactive API docs (Swagger / OpenAPI)

With the app running:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI spec (JSON):** http://localhost:8080/v3/api-docs

The UI lets you try every endpoint from the browser.

## API reference

Base URL: `http://localhost:8080`

### `POST /events` — submit an event

Request body:

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `eventId` | string | yes | Unique id; the basis for idempotency |
| `accountId` | string | yes | Account the event belongs to |
| `type` | string | yes | `CREDIT` or `DEBIT` only |
| `amount` | number | yes | Greater than 0, up to 2 decimal places |
| `currency` | string | yes | 3-letter code, e.g. `USD` |
| `eventTimestamp` | string | yes | ISO-8601 instant; when it originally occurred |
| `metadata` | object | no | Arbitrary extra context |

Responses:
- **`201 Created`** for a new event, with a `Location: /events/{eventId}` header and the stored event.
- **`200 OK`** if the `eventId` was already seen — returns the **original** event unchanged (no
  duplicate is created and the balance is not affected).

```bash
curl -i -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z","metadata":{"source":"mainframe-batch","batchId":"B-9042"}}'
```

### `GET /events/{id}` — fetch one event

```bash
curl http://localhost:8080/events/evt-001
```

- **`200 OK`** with the event, or **`404 Not Found`** if no event has that id.

### `GET /events?account={accountId}` — list an account's events

Always returned **in chronological order by `eventTimestamp`** (ties broken by `eventId`),
regardless of the order events were received. An unknown account returns an empty page.

**Pagination** (optional): `page` (0-based, default `0`) and `size` (default `50`, max `200`). The
sort is fixed server-side so paging never weakens the ordering guarantee. Invalid values return
`400`. The response is an envelope with the page content plus navigation metadata:

```bash
curl "http://localhost:8080/events?account=acct-123&page=0&size=50"
```

```json
{
  "content": [ /* EventResponse objects, chronological */ ],
  "page": 0,
  "size": 50,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### `GET /accounts/{accountId}/balance` — net balance

```
balance = sum(CREDIT amounts) - sum(DEBIT amounts)
```

Computed from the full history with a single aggregate query, so it is correct independent of
arrival order. An account with no events returns a balance of `0.00`.

```bash
curl http://localhost:8080/accounts/acct-123/balance
# {"accountId":"acct-123","balance":150.00}
```

### Error responses

Errors use RFC 7807 (`application/problem+json`). Validation failures list the offending fields:

```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"x","accountId":"a","type":"CREDIT","amount":-5,"currency":"USD","eventTimestamp":"2026-05-15T10:00:00Z"}'
```

```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "errors": [ { "field": "amount", "message": "amount must be greater than 0" } ]
}
```

| Situation | Status |
|---|---|
| Missing required field, zero/negative amount, >2 decimals, bad currency length | `400` |
| Unknown `type`, malformed JSON, invalid timestamp | `400` |
| Missing `account` query parameter on the listing endpoint | `400` |
| Event id not found | `404` |

---

## Design decisions

- **Idempotency by unique constraint — concurrency-safe.** `eventId` carries a unique constraint in
  the database. Submission first looks the event up and returns the original on a repeat. If two
  requests for the same id race past that check simultaneously, both attempt the insert; the
  constraint lets exactly one win and the loser's violation is caught and turned into a normal
  "already exists" response. So simultaneous POSTs behave identically to sequential duplicates —
  one row, balance unmoved (covered by a multithreaded test).
- **Order-independent reads.** Listing relies on `ORDER BY eventTimestamp, eventId` (backed by a
  composite index), and the balance is a single SQL aggregate — neither depends on insertion order.
- **Money as `BigDecimal`.** Amounts are never floating-point. Input is validated to at most two
  decimal places and every monetary value is rendered with a consistent 2-decimal scale.
- **Clear layering.** `web` (controllers, DTOs, error handling) → `service` (transactional logic)
  → `repository`/`domain` (persistence). Request/response DTOs are kept separate from the JPA
  entity so the wire contract and storage model can evolve independently.
- **Consistent errors.** A single `@RestControllerAdvice` maps exceptions to RFC 7807 problem
  documents with meaningful messages and correct status codes.

## Assumptions

- **Accounts are implicit.** There is no separate account registry; an account "exists" by virtue
  of having events. Requesting the balance of an unknown account returns `0.00`, and listing an
  unknown account returns an empty list (both `200`) rather than `404`.
- **Single currency per account.** The balance nets amounts without currency conversion, matching
  the stated `sum(CREDIT) - sum(DEBIT)` formula. Per-currency balances would be a natural extension.
- **Duplicate submissions return `200`**, new submissions return `201` — both include the canonical
  event and a `Location` header.

## Project structure

```
src/main/java/com/eventledger
├─ domain/      Event entity, EventType enum, JSON metadata converter
├─ repository/  EventRepository (lookup, ordered listing, balance aggregate)
├─ service/     EventService (idempotent submit, reads, balance)
└─ web/         Controllers, DTOs, error handling (RFC 7807)
```
