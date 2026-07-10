# AI Usage

This assignment explicitly encourages using AI tooling, so per its instructions: here's
exactly what I used, for what, and what I had to check or fix myself.

## Tool used

**Claude Code** (Anthropic), used as a pair-programmer for the full build: project
scaffolding, domain model, service/business logic, REST controller, the scheduled job,
exception handling, and both the unit and integration test suites.

## What I used it for

- **Scaffolding**: standard Maven/Spring Boot layout (`pom.xml`, package structure) so I
  didn't hand-type boilerplate.
- **Domain model & state machine**: `Order`/`OrderItem` entities and, specifically, the
  `PENDING → PROCESSING → SHIPPED → DELIVERED` (+ `CANCELLED`) transition rules in
  `OrderService`. I reviewed the transition map by hand to make sure it actually matches
  the assignment (cancel only from `PENDING`; no skipping straight to `DELIVERED`).
- **Scheduled job**: the `@Scheduled` background job that promotes `PENDING` orders to
  `PROCESSING` every 5 minutes. I specifically checked that it uses `fixedRate`, not
  `fixedDelay` — the assignment says "every 5 minutes" on a clock, and `fixedDelay` would
  instead wait 5 minutes *after each run finishes*, which is a different (and wrong)
  behavior for this requirement.
- **Tests**: both the Mockito-based service unit tests and the full-stack MockMvc
  integration tests (real H2 database, real HTTP layer) were AI-generated, then run and
  verified rather than taken on faith.
- **Exception handling**: a `@RestControllerAdvice` global handler mapping
  `OrderNotFoundException` → 404 and `InvalidOrderStateException` → 409, instead of
  scattering try/catch blocks through the controller.

## Issues found and corrected

- **Maven wasn't installed on the build machine at all.** `mvn` wasn't on PATH, and the
  usual package managers (`winget`, `choco`) either had no matching package or needed
  admin rights the environment didn't have. Fixed by pulling the portable Apache Maven
  binary distribution directly and running it from there instead of relying on a system
  install.
- **`mvn clean test` failed on Windows** with `Failed to delete ...\target\classes`, a
  file-lock issue (OneDrive sync / an earlier JVM holding the directory open), not a code
  problem. Fixed by running `mvn test` without `clean` for a from-scratch verification.
- With those two environment issues out of the way, the actual test run — 8 Mockito-based
  service unit tests plus 7 full-stack MockMvc integration tests against a real H2
  database — **passed on the first real run: 15/15, 0 failures, 0 errors.** No application
  logic bugs surfaced once it actually ran, which I'm reporting exactly as it happened
  rather than inventing a bug story to sound more thorough than the process actually was.

## Hardening pass (post-review, before submission)

After the initial build was green, I asked for a second review specifically against
software design principles/standards (not just "does it meet the functional spec"). That
surfaced a few real gaps, which I had fixed and re-verified with a full test run rather
than taking on faith:

- The catch-all `@ExceptionHandler(Exception.class)` returned a generic 500 without
  logging the underlying exception — undebuggable in production. Added `log.error(...)`.
- `promoteAllPendingToProcessing()` (the scheduled job's entry point) set the new status
  directly instead of going through the same `ALLOWED_TRANSITIONS` gate as the manual
  `PUT /{id}/status` endpoint — a DRY violation that could silently drift if the state
  machine changed. Extracted a shared `applyTransition` method both paths now call.
- `Order.getItems()` returned the live, mutable internal list — an encapsulation leak.
  Changed to return `Collections.unmodifiableList(...)`.
- No optimistic locking on `Order`, so a scheduler run and a customer's cancel request
  landing at the same instant could silently lose one of the two updates. Added a
  `@Version` field.
- `OrderStatusScheduler` had no test of its own. Added one — and in writing it, hit a real
  environment issue: Mockito's default mock maker can't mock a *concrete class*
  (`OrderService`) on JDK 25, since Byte Buddy's inline retransformation only officially
  supports up to JDK 23. Bumping `mockito.version` to pull in a newer Byte Buddy made
  things *worse* (broke every existing test with a `MockMaker` plugin load failure from a
  version mismatch elsewhere in the dependency graph), so I reverted that and rewrote the
  test to mock `OrderRepository` (an interface — a different, unaffected mocking code
  path) and exercise the real `OrderService` instead. Re-ran the full suite after each
  change rather than assuming the fix worked.

Final state: 17/17 tests passing (15 original + 2 new), verified with a real `mvnd test`
run, not inferred from the diff.

## What I did NOT just accept as-is

- I read every file rather than only running `mvn test` and trusting a green build — a
  test suite can pass while still testing the wrong thing, so I checked the actual
  assertions (e.g. that `cancelOrder` on a non-`PENDING` order returns `409` with a
  message naming the actual current status, not just a generic error).
- I decided myself to add `CANCELLED` as an explicit status (the assignment's status list
  doesn't include it, but cancellation is a required feature) and to add a manual
  `PUT /{id}/status` endpoint beyond the automated `PENDING → PROCESSING` job, since a real
  order system needs some way to reach `SHIPPED`/`DELIVERED` too. Those were my calls, not
  something I let the AI decide unprompted.
