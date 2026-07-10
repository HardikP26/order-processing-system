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

The API is then available at `http://localhost:8080`, and Swagger UI at
`http://localhost:8080/swagger-ui.html`. The H2 console (if you want to inspect the
in-memory DB directly) is at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:orderdb`, user `sa`, no password).

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
`DELIVERED` order at all) is rejected with `409 Conflict`. This lives in one place —
`OrderService.ALLOWED_TRANSITIONS` — so the rules can't drift between endpoints.

## API endpoints

| Method | Path                      | Description                                      |
|--------|---------------------------|---------------------------------------------------|
| POST   | `/api/orders`             | Create an order with one or more items            |
| GET    | `/api/orders/{id}`        | Get a single order by id                           |
| GET    | `/api/orders?status=X`    | List all orders, optionally filtered by status     |
| PUT    | `/api/orders/{id}/cancel` | Cancel an order (only if still `PENDING`)          |
| PUT    | `/api/orders/{id}/status` | Manually advance an order to the next legal status |

### Example: create an order

```bash
curl -X POST http://localhost:8080/api/orders \
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
curl http://localhost:8080/api/orders?status=SHIPPED
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
  scheduler promotes an order to `PROCESSING` at the same moment a customer cancels it.
- **`Order.getItems()` returns an unmodifiable view**, not the live list, so callers can't
  mutate an order's line items without going through `addItem()`.

## AI usage

See [`AI_USAGE.md`](./AI_USAGE.md) for a transparent breakdown of how AI tooling was used
while building this, per the assignment's instructions.
