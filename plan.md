# Implementation plan

Ordered work for the support ticket system. Specs in `spec/` are the source. Do not start tasks 7–10 until task 6 is done and the CRUD/state-machine tests below are green.

RAG (ingestion, embedding, vector store, ask) is tasks 7–10. The frontend is last because the Q&A panel depends on the ask endpoint.

Where two specs disagree, the task that first needs the decision closes it in the spec before code is written. Do not invent an embedding model, top-K, or similarity threshold; those stay open until `spec/evaluation-strategy.md` is run.

## 1. Entities

Ticket and comment only. No ingestion tables in this task.

- `Ticket`: `id`, `title`, `description`, `status`, `priority`, `assignee`, `category`, `createdAt`, `updatedAt` (`spec/data-model.md`).
- `Comment`: `id`, `ticketId`, `author`, `body`, `createdAt`.
- `TicketStatus` and `TicketPriority` as in `spec/state-machine.md` and `spec/requirements.md`. Status stored as the enum name, not an ordinal.
- New tickets start at `OPEN`. Comments are not embedded JSON on the ticket row.
- Close the category gap: add `category` to FR-1, FR-2, and FR-5 (required, 1–64 characters) so `spec/requirements.md` matches the data model, API, and UI. Record why the max length is 64, or change the number.

**Done when:** the schema matches those fields and nullability, and a ticket can be stored with an empty comment list.

## 2. Repositories

- Ticket repository: save, find by id, find by id with comments (oldest comment first).
- Comment repository: insert a comment for a ticket.
- PostgreSQL via migrations. No H2.
- Leave keyword search and status filter to task 6.

**Done when:** a repository test on Testcontainers PostgreSQL round-trips a ticket and its comments and returns comments oldest first.

## 3. Service layer and state machine

`TicketService` use cases, no HTTP types:

- Create → `OPEN`, timestamps set, comments empty.
- Update title, description, priority, assignee, category. Omitted fields stay. Blank assignee clears it. Status does not change. Define `{}`: no field changes and `updatedAt` does not change.
- Add comment, bump ticket `updatedAt`.
- Transition only through the five allowed edges in `spec/state-machine.md`. Every other pair, including self-transitions and anything out of `CLOSED` or `CANCELLED`, throws `TicketStateConflictException` and does not save.

**Tests (required here):** `validTransitions` and `invalidTransitions` parameterized tests plus `transitionMatrix_isExhaustive`, covering all 25 pairs (`spec/test-strategy.md`, `rules/testing.md`). Unit tests use JUnit 5 and Mockito, no Spring context.

**Done when:** those tests pass. A status change in the enum without both sources updated fails the build.

## 4. REST controllers

Thin controllers, constructor injection, DTOs only (`spec/api-contract.md`):

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/api/v1/tickets` | 201 |
| `GET` | `/api/v1/tickets/{id}` | 200 |
| `PATCH` | `/api/v1/tickets/{id}` | 200 |
| `POST` | `/api/v1/tickets/{id}/comments` | 201 |
| `POST` | `/api/v1/tickets/{id}/status` | 200 |
| `GET` | `/api/v1/tickets` | 200 |

- List returns the page envelope (`content`, `page`, `size`, `totalElements`, `totalPages`), never a bare array. Update FR-2 to say that, and remove the “legacy bare array” note in `spec/api-contract.md`.
- Default `page=0`, `size=20`, sort `updatedAt` descending. `size` outside 1–100 and negative `page` are rejected in task 5.
- Page past the last page: 200, empty `content`, `totalElements` unchanged.
- Unknown id on get, update, comment, or transition: 404 `TICKET_NOT_FOUND`.
- Illegal transition: 409 `INVALID_STATUS_TRANSITION`, stored status unchanged.
- Detail includes comments and `allowedTransitions`. List items are summaries (no comments).
- No `DELETE`.

**Done when:** an API test on Testcontainers covers create, get, update (status unchanged), comment, one valid transition, and one 409 (for example `OPEN` → `CLOSED`). The unit matrix from task 3 still owns the other pairs.

## 5. Validation

- Request records: title 1–200, description 1–10000, priority required, assignee ≤120, category 1–64, comment author 1–120, comment body 1–10000. Trim, then validate. Whitespace-only required fields fail.
- Add those comment limits to FR-6.
- `@Valid` on every body. One `GlobalExceptionHandler` returns `ProblemDetail` with `code` and, for field errors, `fields`.
- 400 `VALIDATION_FAILED` for bean validation, unreadable JSON, and bad enums. 500 `INTERNAL_ERROR` with a generic detail and no SQL or stack trace.
- Invalid data is not persisted.

**Done when:** API tests show a blank title and an over-long description return 400 with `fields`, and the ticket count does not change.

## 6. Search and filter

On `GET /api/v1/tickets` only:

- `q`: case-insensitive match on title, description, and comment author/body. Blank `q` is omitted. `%` and `_` in `q` are literal, not wildcards. Write that into FR-7.
- `status`: one `TicketStatus`. Invalid value → 400. Omitted means all statuses.
- `q` and `status` AND, applied before paging.
- Equal `updatedAt`: order by `id` descending. Equal comment `createdAt`: order by comment `id` ascending. Write both into the requirements.

**Done when:** API tests cover keyword hit, keyword miss (empty page, 200), status filter, `q` plus `status`, and a `q` value that contains `%`.

### Gate — CRUD and state machine

Do not start task 7 until all of the following are true:

- Tasks 1–6 are merged and match the specs named above.
- The 25-pair state-machine unit tests pass.
- The API tests for create, list page envelope, detail, update, comment, search, filter, validation 400, not-found 404, and invalid transition 409 pass on PostgreSQL Testcontainers.

## 7. Ingestion pipeline

No embedding client and no vector store in this task.

- Close the chunking contradiction first: `spec/requirements.md` section C and `spec/architecture.md` must say paragraph/section chunking is decided (`spec/rag-ingestion.md`). Model, top-K, and threshold stay open.
- On ticket create, update, transition, or comment, mark the ticket pending or stale after the database commit. The ticket transaction does not build vectors.
- Build one knowledge document per ticket: title header, description split on blank lines, each comment its own chunk (split a comment on blank lines too). Do not merge a description paragraph with a comment.
- Every chunk record stores `ticketId`, `status`, `priority`, `assignee` (null allowed), and `category`. Refuse to keep a chunk that lacks any of those keys.
- Tables: `knowledge_documents`, `ingestion_records` (`PENDING`, `STALE`, `INDEXED`, `FAILED`), `chunk_records`. Replacing a ticket’s chunks deletes the previous set for that `ticketId`.
- `contentHash` skips rebuild when the canonical text and metadata snapshot are unchanged.

**Done when:** a test creates a ticket with a two-paragraph description and two comments, runs the pipeline up to chunk records, and asserts one chunk per paragraph or comment, each with the five metadata fields and the same `ticketId`.

## 8. Embedding job

- Async job reads `PENDING`, `STALE`, and `FAILED` rows. It does not run inside `POST /api/v1/qa`.
- Embedding is behind an interface in `ingestion.infrastructure`. Provider, model, and dimensions are configuration, not constants. Do not enable a real provider until that choice is written into `rules/rag-vector-store.md` with a reason.
- All chunks for one ticket version succeed or the record stays `FAILED` and no partial set is marked `INDEXED`.
- Credentials come from the environment. Tests use a fake embedder that returns a fixed vector.

**Done when:** the fake embedder marks a built ticket `INDEXED`, and a forced failure leaves `FAILED` with the previous index hash unchanged.

## 9. Vector store integration

- Upsert `(chunkId, vector, text, metadata)` and delete that ticket’s previous vectors on re-index.
- Metadata on every point: `ticketId`, `status`, `priority`, `assignee`, `category`.
- The store is a derived index. Ticket rows remain the source of truth for title and status.
- No live network in unit tests. Integration with the store runs only when this task’s adapter is under test.

**Done when:** re-indexing a ticket replaces its points, a fetched point contains the five metadata fields, and a missing metadata field is not written.

## 10. `/api/ai/ask`

One route: `POST /api/v1/qa` (`spec/requirements.md`; review notes call this `/api/ai/ask`). Do not add a second path.

- Body: `question` required, non-blank, max 1000 characters. Response: `question`, `found`, `answer`, `citations[]` of `ticketId`, `title`, `status`.
- Read-only. The handler does not create, update, transition, comment, or wait on the embedding job.
- Citations use the live ticket row for title and status. Drop a hit whose ticket id no longer exists.
- `found=true` only with a non-empty citation list. Otherwise `found=false`, empty citations, and the explicit no-relevant-tickets message.
- FR-17: a ticket or comment just saved must be findable. If its ingestion status is not `INDEXED`, answer from the database (the same lexical match as task 6) instead of an empty vector result.
- Add the stop-word-only question to `spec/evaluation-strategy.md` before calling this done. Q1–Q8 (plus that question) are the evaluation run, separate from the unit build. Unit tests mock the retriever and assert ids, anchors, and the no-match message only.

**Done when:** mocked-retriever unit tests pass for a hit, a comment-only hit, and no-match, and one API test returns citations for a ticket that exists only in PostgreSQL because indexing has not finished.

## 11. Frontend

Follow `spec/ui-flow.md`. The API from tasks 4–6 and 10 must already be in place.

- `/tickets` — summary rows, `q`, status filter, paging. Empty page is an empty state. Errors show `title`, `detail`, and `fields`.
- `/tickets/new` and `/tickets/{id}/edit` — no status control on create; status read-only on edit. 400 keeps typed values and highlights `fields`.
- `/tickets/{id}` — comments oldest first, transition buttons only from `allowedTransitions`, 409 shows `detail` and leaves status as it was, add comment on the same page. Unknown id shows `detail` and a link back to the list.
- Q&A panel on list and detail, calling only `POST /api/v1/qa`. Citations open the ticket. `found=false` shows the server message and no citations. The panel does not write tickets.

**Done when:** the flows above work against the running API, including a validation error, a rejected transition, an empty search, a cited answer, and a no-match answer.
