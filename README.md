# Order Processing System

A backend service for an e-commerce order processing system, built for a take-home
assignment. Java 17 + Spring Boot 3, REST API, H2 in-memory database, and a scheduled
background job that promotes `PENDING` orders to `PROCESSING` every 5 minutes.

## Tech stack

- **Java 17**, **Spring Boot 3.3** (Web, Data JPA, Validation, Scheduling)
- **H2** in-memory database (zero setup — starts fresh on every run)
- **springdoc-openapi** for interactive Swagger UI
- **JUnit 5 + Mockito + AssertJ** for unit tests, **MockMvc** for full-stack integration tests
- **Maven** for build/dependency management

## Getting started

```bash
mvn spring-boot:run
```

The API is then available at `http://localhost:9090`, and Swagger UI at
`http://localhost:9090/swagger-ui.html`. The H2 console (if you want to inspect the
in-memory DB directly) is at `http://localhost:9090/h2-console`
(JDBC URL: `jdbc:h2:mem:orderdb`, user `sa`, no password).

Port defaults to `9090` (see `server.port` in `application.yml`) to sidestep the common
`8080` conflict with other local services (Postgres/pgAdmin, other Spring apps, etc.).
Override it without editing the file via `SERVER_PORT=8081 mvn spring-boot:run` or
`mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`.

Run the test suite:

```bash
mvn test
```

## Domain model

An `Order` has a `customerName`, a list of `OrderItem`s (product name, quantity, price),
and a `status`. `totalAmount` is derived on the fly from the items, never stored, so it
can never drift out of sync with the line items.

Order status moves through a fixed state machine:

```
PENDING --> PROCESSING --> SHIPPED --> DELIVERED
   |
   +--> CANCELLED   (only allowed while still PENDING)
```

Any transition not shown above (e.g. `PENDING` straight to `DELIVERED`, or updating a
`DELIVERED` order at all) is rejected with `409 Conflict`. Enforced by `Order.transitionTo()`
on the entity itself — the single gate every transition (manual endpoint or scheduled job)
passes through, so the rules can't drift between callers.

## API endpoints

| Method | Path                        | Description                                        |
|--------|-----------------------------|-----------------------------------------------------|
| POST   | `/api/orders`               | Create an order with one or more items              |
| GET    | `/api/orders/{id}`          | Get a single order by id                             |
| GET    | `/api/orders?status=X`      | List all orders, optionally filtered by status       |
| GET    | `/api/orders/{id}/history`  | Full status-transition audit trail for an order      |
| PUT    | `/api/orders/{id}/cancel`   | Cancel an order (only if still `PENDING`)            |
| PUT    | `/api/orders/{id}/status`   | Manually advance an order to the next legal status   |

Errors are returned as RFC 7807 `ProblemDetail` (`type`/`title`/`status`/`detail`/`instance`,
plus a `details` array for field-level validation errors) — Spring Boot 3's native error
format, not a hand-rolled shape.

### Example: create an order

```bash
curl -X POST http://localhost:9090/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Hardik Parmar",
    "items": [
      { "productName": "Mechanical Keyboard", "quantity": 1, "price": 75.50 },
      { "productName": "Wireless Mouse", "quantity": 2, "price": 20.00 }
    ]
  }'
```

Returns `201 Created` with the order, `status: "PENDING"`, and `totalAmount: 115.50`.

### Example: list only shipped orders

```bash
curl http://localhost:9090/api/orders?status=SHIPPED
```

## The background job

`OrderStatusScheduler` runs every 5 minutes (`@Scheduled(fixedRateString = ...)`,
configurable via `order.pending-promotion.interval-ms` in `application.yml`) and promotes
every `PENDING` order to `PROCESSING` in one batch. `fixedRate` (not `fixedDelay`) is used
deliberately so the cadence stays on a steady 5-minute clock regardless of how long a
given run takes.

## Design decisions & assumptions

- **H2 in-memory, not Postgres/MySQL**: this is a take-home assignment meant to run with
  zero setup on a reviewer's machine. Swapping in a real database is a `pom.xml` +
  `application.yml` change — Spring Data JPA is already the abstraction doing the work.
- **`CANCELLED` added as a status**: the assignment lists `PENDING/PROCESSING/SHIPPED/DELIVERED`
  but also requires order cancellation, so a `CANCELLED` terminal state was added rather
  than reusing an existing one, to keep cancelled orders distinguishable from ones still
  in the normal flow.
- **A manual `PUT /{id}/status` endpoint was added** beyond what was strictly specified,
  since the assignment only automates `PENDING → PROCESSING`, and a real order system needs
  some way to move orders through `SHIPPED`/`DELIVERED` too. It enforces the same legal-transition
  rules as the scheduled job.
- **`totalAmount` is computed, not stored**, to guarantee it's always consistent with the
  order's actual line items.
- **All status transitions — manual and scheduled — go through one gate**
  (`OrderService.applyTransition`), so the legal-transition rules can't drift between the
  `PUT /{id}/status` endpoint and the background job.
- **Optimistic locking (`@Version`) on `Order`**: guards against a lost update if the
  scheduler promotes an order to `PROCESSING` at the same moment a customer cancels it —
  the loser gets a `409 Conflict`, not a silently overwritten row. Covered by
  `OrderConcurrencyTest`, which simulates two stale readers racing to write.
- **`Order.getItems()` returns an unmodifiable view**, not the live list, so callers can't
  mutate an order's line items without going through `addItem()`.
- **`OrderStatusHistory`** records every transition (from/to status, timestamp, and which
  code path drove it — `API` or `SCHEDULER`) via `GET /api/orders/{id}/history`. No `actor`
  field: this system has no authentication/identity concept, so there's no real actor to
  record — adding a fake one would be worse than omitting it.
- **`items` is lazy-loaded** (`FetchType.LAZY`) with `@EntityGraph` on the repository's
  read queries, so list/get endpoints fetch items in one batched query instead of N+1,
  without eagerly loading items everywhere by default.
- **Actuator + Micrometer**: `/actuator/health` and `/actuator/metrics`, plus scheduler-specific
  counters (`orders.scheduler.promoted`, `orders.scheduler.failures`) and a timer
  (`orders.scheduler.duration`).
- **Correlation ID**: every request gets an `X-Correlation-Id` (reused from the request
  header if the caller sends one), echoed in the response and available to every log line
  via MDC — `%X{correlationId}` in the console pattern.

## Deliberately not done

A few "production readiness" items were considered and skipped, on purpose, not by omission:

- **State Pattern (class per status)** instead of the transition-gate approach — five
  states don't justify five classes and an interface; would be premature abstraction.
- **Flyway migrations** — contradicts the zero-setup, in-memory-H2 design; Flyway earns its
  keep on a persistent DB with schema evolving across deployments, not a DB that resets
  every run.
- **Pagination on `GET /api/orders`** — not in the assignment spec, and changes the response
  from a plain array to a wrapped page, breaking the existing contract for no real benefit
  at this scale.
- **Spring Security + JWT, rate limiting** — no auth/rate-limiting requirement in the spec;
  both are significant new scope with real risk of shipping half-finished this close to a
  deadline.
- **MapStruct** — the 4 hand-written DTO factory methods are trivial and explicit; a codegen
  mapper would trade readability for no real gain here.

## AI usage

See [`AI_USAGE.md`](./AI_USAGE.md) for a transparent breakdown of how AI tooling was used
while building this, per the assignment's instructions.
