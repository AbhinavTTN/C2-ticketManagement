# Spring Boot 3 + Java 21 (ticket management)

Apply when changing Java, REST, persistence, or API contracts.

Stack: Spring Boot 3, Java 21, Spring Web, Spring Data JPA. Follow the patterns already in `ticket/` and `common/exception/`.

## Package structure

Use a **feature-first** layout under `com.company.ticketmanagement`. Shared types live in `common`; ticket (and later comment/user) code lives in its own feature package.

```
com.company.ticketmanagement
├── TicketManagementApplication.java
├── config/                 # CORS, OpenAPI, Jackson, persistence — no business rules
├── common/
│   ├── exception/          # AppException, ErrorCode, GlobalExceptionHandler
│   └── dto/                # Shared API wrappers only (page, error body)
└── ticket/
    ├── api/                # REST controllers
    ├── application/        # Services, use-case orchestration, @Transactional
    ├── domain/             # JPA entities, enums, domain exceptions
    ├── dto/                # Request/response records only
    ├── mapper/             # Entity ↔ DTO mapping
    └── infrastructure/     # Spring Data repositories
```

- One public type per file; names match the layer (`TicketController`, `TicketService`, `TicketRepository`).
- Do not put entities, DTOs, and controllers in a flat package.
- New features get a sibling of `ticket/` with the same inner layers. Do not grow a technical-layer tree (`controller/`, `service/` at the root).

## Layering

| Layer | Allowed | Forbidden |
| --- | --- | --- |
| **Controller** (`api`) | HTTP mapping, query params, `@Valid`, call service, HTTP status via `@ResponseStatus` | Business rules, `@Transactional`, injecting repositories, returning entities |
| **Service** (`application`) | Use cases, invariants, transactions, orchestration, mapping via mapper | `HttpServlet*`, status codes, leaking persistence internals to the API |
| **Repository** (`infrastructure`) | Spring Data queries, entity persistence | HTTP, DTO construction, business branching |
| **Domain** | Entities, enums, invariants (`transitionTo`, `updateDetails`), domain exceptions | REST types, Spring Web annotations |

```java
// BAD: controller talks to the repository and returns an entity
@GetMapping("/{id}")
public Ticket get(@PathVariable Long id) {
    return ticketRepository.findById(id).orElseThrow();
}

// GOOD: controller stays thin; service owns the use case
@GetMapping("/{id}")
public TicketResponse get(@PathVariable Long id) {
    return ticketService.getById(id);
}
```

- Inject by constructor (no field `@Autowired`).
- Keep ticket REST on `/api/v1/tickets` (resource names, versioned prefix). Other surfaces use `/api/v1/...` (e.g. `/api/v1/qa`).
- Put `@Transactional(readOnly = true)` on the **service class**. Override with `@Transactional` on write methods. Do not put transactions on repositories or controllers.
- Keep workflow on the entity (`ticket.transitionTo(...)`, `ticket.addComment(...)`). The service loads, calls, and maps; it does not scatter status if-else.
- Controllers return DTOs directly. Use `@ResponseStatus(HttpStatus.CREATED)` for creates; default 200 otherwise. Do not wrap every call in `ResponseEntity` unless headers or a non-standard status are required.

## Exception handling

- Throw **domain/application exceptions** from services and entities (`TicketNotFoundException`, `TicketStateConflictException`). Do not catch-and-return error maps in controllers.
- Domain exceptions extend `AppException` and carry a stable `ErrorCode`.
- Handle them in a single `@RestControllerAdvice` (`GlobalExceptionHandler`) that writes Spring Boot 3 `ProblemDetail` (RFC 7807). Set `title`, `detail`, and property `code` (`errorCode.name()`).
- Map consistently:
  - not found → 404 (`TICKET_NOT_FOUND`)
  - validation / malformed JSON / type mismatch → 400 (`VALIDATION_FAILED`)
  - illegal state / conflict → 409 (`INVALID_STATUS_TRANSITION`)
  - unhandled → 500 (`INTERNAL_ERROR`) with a generic message (never leak SQL, stack traces, or exception class names)

```java
// BAD
@GetMapping("/{id}")
public ResponseEntity<?> get(@PathVariable Long id) {
    return ticketRepository.findById(id)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(404).body(Map.of("error", "missing")));
}

// GOOD
public TicketResponse getById(Long id) {
    return ticketMapper.toResponse(requireTicket(id));
}

private Ticket requireTicket(Long id) {
    return ticketRepository.findByIdWithComments(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
}
```

```java
@ExceptionHandler(TicketNotFoundException.class)
ProblemDetail notFound(TicketNotFoundException ex) {
    var body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    body.setTitle("Ticket not found");
    body.setProperty("code", ex.getCode().name());
    return body;
}
```

- Add a new `ErrorCode` when introducing a new client-visible failure mode. Do not reuse `INTERNAL_ERROR` for expected business failures.
- Validation field errors belong in `ProblemDetail` property `fields` (map of field → message), not in `detail` as a concatenated string.

## DTO vs entity

- **Entities** (`@Entity` in `domain`) are persistence models: IDs, relations, JPA annotations, domain methods. Never serialize them on the API (no entities as `@RequestBody` or return types).
- **DTOs** are Java **records** in `ticket.dto`. They carry only what the client may see or send:
  - write: `CreateTicketRequest`, `UpdateTicketRequest`, `TransitionTicketRequest`, `AddCommentRequest`
  - read: `TicketResponse`, `TicketSummaryResponse`, `CommentResponse`
- Map in `ticket.mapper` (`TicketMapper`), called from the **service**. Controllers must not call entity setters or construct entities.
- List endpoints return summaries; detail endpoints return full responses (including comments and `allowedTransitions`).
- Do not nest full related entities in responses; use ids or small nested response records (`CommentResponse`).
- Jakarta Validation (`@NotBlank`, `@Size`, `@NotNull`) belongs on **request DTOs**, not on entities. Entity `@Column` constraints stay for persistence only.
- Domain enums (`TicketStatus`, `TicketPriority`) may appear on DTOs when they are the public contract. Do not expose lazy collections or JPA proxies.

```java
// BAD
public Ticket create(@RequestBody Ticket ticket) { ... }

// GOOD
public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) { ... }
```
