# Support Ticket Management System — functional requirements

Each requirement is numbered and has an observable pass/fail condition. HTTP paths, status codes, and error bodies follow `rules/api-standards.md`. Domain layout follows `rules/java-springboot.md`. Tests follow `rules/testing.md`.

**Out of scope (not assumed):** authentication, roles, multi-tenancy, email, attachments, SLA timers, rate limits. A spec that needs any of these must add them as new numbered requirements.

**Terms (single meaning throughout):**

| Term | Values |
| --- | --- |
| Status | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` |
| Priority | `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| Ticket fields | `id`, `title`, `description`, `status`, `priority`, `assignee`, `createdAt`, `updatedAt`, `comments` |
| Comment fields | `id`, `author`, `body`, `createdAt` |
| Error body | RFC 7807 `ProblemDetail` with `title`, `detail`, `status`, and `code` |

---

## A. Ticket CRUD, search, persistence, and lifecycle

### FR-1 Create a ticket

A client can create a ticket by submitting `title` (required, 1–200 characters after trim), `description` (required, 1–10000 characters after trim), `priority` (required, one of the priority values), and optional `assignee` (at most 120 characters).

**Pass:** The ticket is stored with `status=OPEN`, server-assigned `id`, `createdAt`/`updatedAt` set, empty comments, and the create response returns that ticket. HTTP create is `201`.

### FR-2 List tickets

A client can retrieve a list of stored tickets without supplying an id.

**Pass:** HTTP `200`. Each list item includes at least `id`, `title`, `status`, `priority`, `assignee`, `updatedAt` (summary — not full comments). An empty store returns `200` with an empty collection, not `404`. Default order is newest `updatedAt` first.

### FR-3 View ticket details

A client can retrieve one ticket by `id`.

**Pass:** HTTP `200` with full fields including comments (oldest first) and the set of currently allowed status transitions for that ticket.

### FR-4 Unknown ticket

Get, update, status change, or add-comment against an id that does not exist fails.

**Pass:** HTTP `404`, `code=TICKET_NOT_FOUND`. No new row is written.

### FR-5 Update title, description, priority, and assignee

A client can change `title`, `description`, `priority`, and/or `assignee` on an existing ticket. Status is **not** changed by this operation (see FR-11). Omitted fields are left unchanged. Blank `assignee` clears the assignee.

**Pass:** HTTP `200` with the updated ticket. `updatedAt` changes. Status and comments are unchanged except as implied by `updatedAt`.

### FR-6 Add comments

A client can add a comment to an existing ticket with `author` and `body` (both required, non-blank after trim).

**Pass:** HTTP `201`. The comment is persisted on that ticket, appears in subsequent detail views, and `updatedAt` on the ticket changes.

### FR-7 Search tickets by keyword

A client can list tickets with an optional keyword `q`. A ticket matches if the keyword appears (case-insensitive) in title, description, or any comment `author`/`body`.

**Pass:** Only matching tickets are returned. No matches → `200` and an empty collection. Blank `q` is treated as omitted (unfiltered by keyword).

### FR-8 Filter tickets by status

A client can list tickets with an optional `status` equal to one status value.

**Pass:** Only tickets with that status are returned. Combined with `q`, both constraints apply (AND). Invalid `status` → HTTP `400`, `code=VALIDATION_FAILED`.

### FR-9 Persist data in a database

Tickets and comments survive process restart. Listing, detail, search, and QA (section B) read the same stored records.

**Pass:** After restart, previously created tickets and comments are still returned by FR-2/FR-3.

### FR-10 Validate input at the backend

Create, update, comment, and status-change requests are validated server-side. Clients cannot bypass rules by omitting the UI.

**Pass:** Missing/blank required fields, over-length strings, unreadable JSON, and illegal enum values → HTTP `400`, `code=VALIDATION_FAILED`. Field-level messages appear in `fields` for Bean Validation failures. Invalid data is not persisted.

### FR-11 Status state machine

Status changes only through an explicit transition, not through FR-5. Allowed transitions:

| From | To |
| --- | --- |
| `OPEN` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED`, `CANCELLED` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | _(none)_ |
| `CANCELLED` | _(none)_ |

**Pass:** A valid transition returns HTTP `200` with the new status and a later `updatedAt`. Self-transitions (`OPEN` → `OPEN`, etc.) are invalid (FR-12).

### FR-12 Invalid transitions are rejected

Any pair not listed in FR-11 is rejected, including the examples: `CLOSED` → `OPEN`, `RESOLVED` → `OPEN`, `CANCELLED` → `OPEN`.

**Pass:** HTTP `409`, `code=INVALID_STATUS_TRANSITION`. Stored status is unchanged. Every `(from, to)` pair of the status enum is covered by tests (valid and invalid) per `rules/testing.md`.

### FR-13 Display meaningful errors in the UI

The UI shows backend errors in language a support user can act on. It uses `title`, `detail`, and (when present) `fields` from the error body — not raw stack traces, SQL, or HTTP-only codes.

**Pass:** Validation failures highlight the offending fields. Not-found and invalid-transition cases show the server `detail`. Unexpected failures show a generic message consistent with `INTERNAL_ERROR` (no internals leaked).

---

## B. Natural-language question answering over ticket history

QA is a **read-only** feature. It must not create, update, transition, or comment on tickets. HTTP surface: `POST /api/v1/qa` (also referred to as `/api/ai/ask` in review commands). Request: `{ "question": "..." }` (required, non-blank, max 1000 characters). Response: `{ "question", "found", "answer", "citations" }` where each citation is `{ "ticketId", "title", "status" }`.

### FR-14 Grounded answers only

When the system answers a question, every factual claim in `answer` is taken from stored ticket fields and comments (title, description, status, priority, assignee, comment author/body, timestamps as stored). The system must not invent causes, ETAs, resolutions, or tickets that are not in the database.

**Pass:** Review of the answer against the cited tickets (see `commands/review-rag-output.md`) finds no fabricated claims.

### FR-15 Cite the tickets used

When `found=true`, `citations` is non-empty and lists **every** ticket whose data was used to produce `answer`. Citation `ticketId`, `title`, and `status` match the stored ticket.

**Pass:** An answer with `found=true` and empty `citations` is a defect. Named tickets in the answer appear in `citations`.

### FR-16 No relevant tickets — do not fabricate

When no stored ticket is relevant to the question (including a named ticket id that does not exist), the system must not invent an answer.

**Pass:** `found=false`, `citations` is empty, and `answer` explicitly states that no relevant tickets were found. `found=true` must not appear together with that no-match message.

### FR-17 QA uses the same persisted history as CRUD

Relevance is judged against tickets and comments written by section A, including comments. QA does not use a separate unpublished corpus.

**Pass:** A question whose terms match a just-created ticket (FR-1) or comment (FR-6) can retrieve that ticket. A question about content that was never stored follows FR-16.

---

## C. Open questions (do not invent defaults)

These are **not** requirements until decided during spec analysis. Do not pick values in code or tests first. See `rules/rag-vector-store.md`.

1. **Chunking strategy** — What unit is indexed (whole ticket, fields, comments, windows)? How does a chunk map back to a ticket id for FR-15?
2. **Embedding model** — Provider, model, dimensions, hosted vs self-hosted, configuration (no hardcoded secrets).
3. **Top-K and similarity threshold** — How many candidates, what cutoff counts as “relevant” for FR-16, and how that cutoff will be evaluated.

Until those are decided, lexical retrieval over stored ticket text is acceptable **provided FR-14–FR-17 still hold**.
