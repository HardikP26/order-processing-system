# Order Processing System

A backend service for an e-commerce order processing system, built for a take-home
assignment. Java 17 + Spring Boot 3, REST API, H2 in-memory database (schema managed by
Flyway), and a scheduled background job that promotes `PENDING` orders to `PROCESSING`
every 5 minutes.

## Tech stack

- **Java 17**, **Spring Boot 3.3** (Web, Data JPA, Validation, Scheduling, Actuator)
- **H2** in-memory database, schema versioned via **Flyway**
- **Micrometer** metrics, **Bucket4j** rate limiting
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
`DELIVERED` order at all) is rejected with `409 Conflict`. Implemented with the **State
pattern** (`com.hardik.orderprocessing.model.state`) — one class per status, each owning
its own legal next-statuses — rather than a shared lookup table, so `Order.transitionTo()`
just delegates to whichever state matches the order's current status. `OrderStatus` (the
enum) remains the persisted representation; the state classes are purely the behavior
layer on top of it. Manual endpoint and scheduled job both call the same
`transitionTo()`, so the rules can't drift between callers.

## API endpoints

All endpoints are versioned under `/api/v1`.

| Method | Path                          | Description                                                     |
|--------|-------------------------------|-------------------------------------------------------------------|
| POST   | `/api/v1/orders`              | Create an order with one or more items                            |
| GET    | `/api/v1/orders/{id}`         | Get a single order by id                                           |
| GET    | `/api/v1/orders?status=X`     | List orders, optionally filtered by status                        |
| GET    | `/api/v1/orders?page=&size=`  | Same, paginated. Omit page/size to get every matching order back  |
| GET    | `/api/v1/orders/{id}/history` | Full status-transition audit trail for an order                   |
| PUT    | `/api/v1/orders/{id}/cancel`  | Cancel an order — only from `PENDING`. Idempotent (200 no-op if already cancelled) |
| PUT    | `/api/v1/orders/{id}/status`  | Manually advance an order to the next legal status                |

Errors are returned as RFC 7807 `ProblemDetail` (`type`/`title`/`status`/`detail`/`instance`,
plus a `details` array for field-level validation errors) — Spring Boot 3's native error
format, not a hand-rolled shape.

Requests to `/api/**` are rate-limited to 60/minute per client IP (in-memory token bucket,
`RateLimitFilter`) — returns `429` past that.

### Example: create an order

```bash
curl -X POST http://localhost:9090/api/v1/orders \
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

### Example: list only shipped orders, paginated

```bash
curl "http://localhost:9090/api/v1/orders?status=SHIPPED&page=0&size=10"
```

## The background job

`OrderStatusScheduler` runs every 5 minutes (`@Scheduled(fixedRateString = ...)`,
configurable via `order.pending-promotion.interval-ms` in `application.yml`) and promotes
every `PENDING` order to `PROCESSING` in one batch. `fixedRate` (not `fixedDelay`) is used
deliberately so the cadence stays on a steady 5-minute clock regardless of how long a
given run takes. Each run is timed and counted via Micrometer
(`orders.scheduler.promoted`, `orders.scheduler.failures`, `orders.scheduler.duration`),
visible at `/actuator/metrics`.

## Design decisions & assumptions

- **H2 in-memory, not Postgres/MySQL**: zero setup on a reviewer's machine. Schema is
  still managed properly via **Flyway** (`src/main/resources/db/migration`), with
  `ddl-auto: validate` — Hibernate checks the schema matches the entities but never
  mutates it. Swapping to a real Postgres/MySQL instance is a `pom.xml` + `datasource.url`
  change; the migration already targets standard SQL.
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
- **Optimistic locking (`@Version`) on `Order`**: guards against a lost update if the
  scheduler promotes an order to `PROCESSING` at the same moment a customer cancels it —
  the loser gets a `409 Conflict`, not a silently overwritten row. Covered by
  `OrderConcurrencyTest`, which simulates two stale readers racing to write.
- **`Order.getItems()` returns an unmodifiable view**, not the live list, so callers can't
  mutate an order's line items without going through `addItem()`.
- **`OrderStatusHistory`** records every transition (from/to status, timestamp, and which
  code path drove it — `API` or `SCHEDULER`) via `GET /api/v1/orders/{id}/history`. No
  `actor` field: this system has no authentication/identity concept, so there's no real
  actor to record — adding a fake one would be worse than omitting it.
- **`items` is lazy-loaded** (`FetchType.LAZY`) with `@EntityGraph` on the repository's
  read queries, so list/get endpoints fetch items in one batched query instead of N+1,
  without eagerly loading items everywhere by default.
- **Cancel is idempotent**: cancelling an already-`CANCELLED` order returns `200` (its
  current state) rather than `409` — repeating the same request shouldn't change the
  outcome, matching `PUT`'s idempotency contract. Any other non-`PENDING` status (e.g.
  `SHIPPED`) is still a genuine `409` conflict, since that's not a repeat of the same action.
- **Request/response DTOs are Java `record`s** — immutable by construction, no manual
  getter/setter boilerplate, validation annotations sit directly on the record components.
- **API versioned under `/api/v1`** — no spec requirement for it, but costs nothing and is
  the conventional first move for any REST API meant to evolve.
- **Rate limiting (Bucket4j, in-memory)** on `/api/**` — sized conservatively (60/min/IP)
  to demonstrate abuse-protection awareness; a single-instance in-memory bucket is
  appropriate here, a distributed store (Redis) would be the move for multiple instances.
- **Actuator + Micrometer**: `/actuator/health` and `/actuator/metrics`, plus scheduler-specific
  counters and a timer (see above).
- **Correlation ID**: every request gets an `X-Correlation-Id` (reused from the request
  header if the caller sends one), echoed in the response and available to every log line
  via MDC — `%X{correlationId}` in the console pattern.

## Deliberately not done

A few items were considered and skipped, on purpose, not by omission:

- **Splitting `OrderService` into multiple services** — it's ~130 lines, one aggregate
  (`Order`), cohesive methods. Splitting it would fragment a single responsibility for no
  reason, actively hurting cohesion rather than improving it.
- **A bulk `@Modifying` JPQL update for the scheduler** (`UPDATE Order SET status=...`) —
  would be faster for very large PENDING batches, but bypasses two things already built
  deliberately: the State-pattern transition gate (`Order.transitionTo()`) and the
  `OrderStatusHistory` audit trail (bulk updates don't load entities, so there'd be nothing
  to record history from). The correctness/auditability cost isn't worth the perf gain at
  this scale.
- **Spring Security + JWT** — no auth requirement in the spec, and there's no user/identity
  model anywhere in this system for a token to represent. Bolting on auth just to check a
  box would be the least defensible kind of scope creep — a real "why does this exist"
  question with no good answer.
- **MapStruct** — the DTOs are now `record`s with 3-line static factory methods
  (`OrderResponse.from(order)`); a codegen mapper would replace something already minimal
  and explicit with an annotation-processor dependency, for no measurable gain.

## AI usage

See [`AI_USAGE.md`](./AI_USAGE.md) for a transparent breakdown of how AI tooling was used
while building this, per the assignment's instructions.
