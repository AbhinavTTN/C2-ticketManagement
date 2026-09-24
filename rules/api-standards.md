# API standards (REST)

Apply when changing HTTP endpoints, request/response JSON, status codes, or error bodies.

JSON is camelCase. Enums are strings (`OPEN`, `HIGH`). Instant fields are ISO-8601 UTC. Do not expose JPA entities. See `rules/java-springboot.md` for DTO vs entity.

## REST conventions

All ticket and QA routes live under `/api/v1`. Resource names are plural nouns. Do not put verbs in the path (`/getTicket`). Nested actions that are not CRUD on the ticket body use a sub-resource (`/status`, `/comments`).

| Method | Path | Success | Body |
| --- | --- | --- | --- |
| `POST` | `/api/v1/tickets` | 201 | `CreateTicketRequest` → `TicketResponse` |
| `GET` | `/api/v1/tickets` | 200 | filters + pagination → page of `TicketSummaryResponse` |
| `GET` | `/api/v1/tickets/{id}` | 200 | `TicketResponse` (comments + `allowedTransitions`) |
| `PATCH` | `/api/v1/tickets/{id}` | 200 | `UpdateTicketRequest` (partial: title, description, priority, assignee) |
| `POST` | `/api/v1/tickets/{id}/status` | 200 | `TransitionTicketRequest` → `TicketResponse` |
| `POST` | `/api/v1/tickets/{id}/comments` | 201 | `AddCommentRequest` → `TicketResponse` |
| `POST` | `/api/v1/qa` | 200 | `TicketQaRequest` → `TicketQaResponse` |

- Use `PATCH` for partial ticket field updates. Do not change `status` via `PATCH`; clients must `POST .../status`.
- Use `@Valid` on every request body. Controllers return DTOs directly; use `@ResponseStatus(HttpStatus.CREATED)` for creates.
- Query params for list filters: `status` (`TicketStatus`), `q` (keyword across title, description, comments). Do not add `/tickets/open` style paths.
- Missing ticket → 404. Illegal transition → 409. Validation / bad JSON / bad enum or param → 400. Unknown failures → 500.
- Empty list is `200` with an empty `content` array, not `404`.

```java
// BAD: verb in path, entity in/out, status mixed into PATCH
@PutMapping("/updateTicket/{id}")
public Ticket update(@PathVariable Long id, @RequestBody Ticket ticket) { ... }

// GOOD
@PatchMapping("/{id}")
public TicketResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) { ... }

@PostMapping("/{id}/status")
public TicketResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionTicketRequest request) { ... }
```

## Error response shape

All errors are Spring Boot 3 `ProblemDetail` (RFC 7807) from `GlobalExceptionHandler`. Clients read `status`, `title`, `detail`, and `code`. Do not return ad-hoc `{ "error": "..." }` maps.

| HTTP | `code` | When |
| --- | --- | --- |
| 404 | `TICKET_NOT_FOUND` | Unknown ticket id |
| 409 | `INVALID_STATUS_TRANSITION` | State machine rejected the change |
| 400 | `VALIDATION_FAILED` | Bean Validation, unreadable JSON, type mismatch |
| 500 | `INTERNAL_ERROR` | Unexpected; generic `detail` only — no SQL or stack traces |

Validation errors also set `fields`: map of field name → message.

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed. Correct the highlighted fields and try again.",
  "code": "VALIDATION_FAILED",
  "fields": {
    "title": "Title is required."
  }
}
```

```json
{
  "title": "Invalid ticket status transition",
  "status": 409,
  "detail": "Cannot transition a ticket from OPEN to CLOSED.",
  "code": "INVALID_STATUS_TRANSITION"
}
```

Add a new `ErrorCode` when introducing a new client-visible failure. Keep `code` equal to `ErrorCode.name()`.

## Pagination (ticket listing)

`GET /api/v1/tickets` **must** return a page envelope, never a bare JSON array. Filters (`status`, `q`) apply before paging. Default order is `updatedAt` descending (newest activity first).

Query params:

| Param | Default | Rules |
| --- | --- | --- |
| `page` | `0` | 0-based; reject negative with 400 |
| `size` | `20` | 1–100 inclusive; reject out of range with 400 |
| `status` | omitted | optional `TicketStatus` |
| `q` | omitted | optional keyword; blank treated as omitted |

Response body (record in `ticket.dto` or `common.dto`):

```json
{
  "content": [
    {
      "id": 1,
      "title": "Email bounce",
      "status": "OPEN",
      "priority": "HIGH",
      "assignee": "Riley",
      "updatedAt": "2026-09-24T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- `content` is `TicketSummaryResponse` only (no comments, no `allowedTransitions`). Detail stays on `GET /api/v1/tickets/{id}`.
- `totalPages` is `0` when `totalElements` is `0`.
- Do not use cursor/Link-header pagination unless a new API version requires it.
- Repository paging must happen in the database (`Pageable`), not `stream().skip().limit()` in memory.

```java
// BAD
@GetMapping
public List<TicketSummaryResponse> list(...) { ... }

// GOOD
@GetMapping
public TicketPageResponse list(
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) { ... }
```

## Versioning

- Version is in the **URI**: `/api/v1/...`. Do not version with custom headers or content-type profiles.
- Additive, backward-compatible changes stay on `v1` (new optional request fields, new response fields, new endpoints).
- Breaking changes (rename/remove fields, change status codes, change list from array to page, change enum values) require `/api/v2/...` and a new controller mapping. Do not break `v1` in place.
- The current list response is a raw array in older `v1` code; new listing work must use the page envelope above. If clients already depend on a bare array, introduce the envelope on `v1` only when no clients exist, otherwise add it on `v2` and keep `v1` stable.
- QA and tickets share the same major version (`/api/v1/qa` with `/api/v1/tickets`).
