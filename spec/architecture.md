# Architecture

Target module layout and the RAG ingestion pipeline. Complements `spec/requirements.md` (what) and `spec/data-model.md` (entities). Layering inside each module follows `rules/java-springboot.md`. Retrieval/indexing rules in `rules/rag-vector-store.md` apply; **chunk size, embedding model, and top-K/similarity threshold remain open** — this document names the stages, not those values.

## Principles

- Feature packages under `com.company.ticketmanagement`, each with `api` / `application` / `domain` / `dto` / `mapper` / `infrastructure` as needed. Shared exceptions stay in `common`.
- Ticket writes never call the embedding API. Ingestion is a **separate write path** from `POST /api/v1/qa`.
- The ask module is **read-only** against tickets/comments (FR-14–FR-17). It may read the vector store and the relational ticket rows for citation metadata.
- Vector-store and embedding SDK types stay in `infrastructure`. DTOs and controllers never import them.
- Until the three RAG open questions are decided, lexical retrieval in the ask module remains an allowed fallback, still bound to FR-14–FR-17.

## Module structure

```
com.company.ticketmanagement
├── TicketManagementApplication.java
├── config/
├── common/exception/
├── ticket/          # ticket module
├── comment/         # comment module
├── ingestion/       # RAG ingestion module
└── ask/             # ask-endpoint module
```

### Ticket module (`ticket`)

**Owns:** Ticket aggregate root (identity, title, description, status, priority, assignee, category, timestamps), `TicketStatus` state machine (FR-11/FR-12), list/search/filter (FR-2, FR-7, FR-8), detail (FR-3), create/update (FR-1, FR-5), persistence.

**HTTP:** `POST/GET/PATCH /api/v1/tickets`, `POST /api/v1/tickets/{id}/status`.

**Depends on:** `comment` for loading/saving comments on a ticket; does **not** depend on `ingestion` or `ask`. After a successful write (create, update, transition), publishes a domain event (`TicketChanged`) that ingestion listens to. The ticket service does not embed or upsert vectors.

**Must not:** Change status via PATCH; return entities on the API; inject vector-store clients.

### Comment module (`comment`)

**Owns:** `Comment` entity (body, author, timestamps, FK to ticket), add-comment use case (FR-6), comment persistence and ordering (oldest first on detail).

**HTTP:** `POST /api/v1/tickets/{id}/comments` may live on a thin controller in `comment.api` (path still nested under tickets). Ticket detail composes comments via the comment module’s application API, not by reaching into comment tables from the ticket repository except through that API or an explicit join owned by ticket **read** models.

**Depends on:** `ticket` only to verify the ticket exists (FR-4) before insert. After a successful comment, publishes `TicketChanged` (same event as ticket writes) so ingestion re-indexes the parent ticket.

**Must not:** Transition ticket status; call embeddings.

### RAG ingestion module (`ingestion`)

**Owns:** Building a **knowledge document** from a ticket + its comments; splitting that document into **chunks**; sending chunk text to the embedding client; upserting vectors + metadata into the vector store; **ingestion-tracking** entities (see `spec/data-model.md`).

**HTTP:** None required for FR-14–FR-17. Optional operator endpoints (reindex one ticket / all stale) are out of scope unless added as new FRs.

**Depends on:** `ticket` and `comment` **read** APIs (or repositories) to load source data. Embedding client and vector-store adapter in `ingestion.infrastructure`.

**Trigger:** Async handler of `TicketChanged` (and a periodic sweep of `STALE` / `FAILED` tracking rows). Work runs **after** the ticket transaction commits. Ask requests must not enqueue or wait on this pipeline.

**Must not:** Mutate ticket/comment fields; invent `ticketId` or other metadata; call into `ask`.

### Ask-endpoint module (`ask`)

**Owns:** `POST /api/v1/qa` (FR-14–FR-17): validate question, retrieve relevant chunks (vector search once parameters are decided, else lexical fallback), load cited tickets for titles/status, compose `answer` **only** from retrieved/stored text, set `found` / `citations`.

**HTTP:** `POST /api/v1/qa` only. Read-only.

**Depends on:** Vector-store **query** adapter (infrastructure); ticket **read** for citation fields (`ticketId`, `title`, `status`). May use a `TicketRetriever` interface so lexical and vector implementations swap without changing the controller.

**Must not:** Write tickets, comments, or ingestion rows; embed or upsert inside the request if that would couple ask to ingestion latency (query-time embedding of the **question** is allowed once a model is chosen; indexing tickets is not).

```
  UI ──► ticket / comment APIs ──► PostgreSQL (source of truth)
                 │
                 └── TicketChanged ──► ingestion ──► vector store
                                                    ▲
  UI ──► ask API ── retrieval ─────────────────────┘
                 └── citations from ticket reads
```

## Ingestion pipeline

Source of truth is always the relational ticket + comments (FR-9, FR-17). The vector store is a derived index. If they disagree, ticket rows win for citations and for “what is true.”

```
Ticket (+ comments)
    → Knowledge document
    → Chunks (each carrying required metadata)
    → Embedding
    → Vector store upsert
```

### 1. Ticket (source)

Load ticket by id with comments (same records CRUD uses). If the ticket was deleted (not in current FRs) or missing, mark tracking `FAILED` / skip — do not write orphan vectors.

### 2. Knowledge document

A **knowledge document** is the canonical, citation-ready text for one ticket at one content version:

- Identity: `ticketId`
- Snapshot metadata (copied onto every later chunk): `ticketId`, `status`, `priority`, `assignee`, `category`
- Body: a deterministic concatenation of stored fields (title, description, comments in order, plus the snapshot metadata as text if needed for retrieval). Exact concatenation/chunking **layout** is open (chunking strategy).
- `contentHash` over that canonical body + metadata so the pipeline can skip embed when nothing changed.

Persist or replace the `KnowledgeDocument` row (see data model) with this snapshot **before** embedding.

### 3. Chunk

Split the knowledge document into one or more chunks. **How** (whole ticket vs windows vs per-comment, size, overlap) is an open question — the pipeline still has this stage.

**Invariant (not open):** every chunk written to the vector store includes **all** required metadata: `ticketId`, `status`, `priority`, `assignee`, `category`. Missing any field → do not upsert that chunk (tracking `FAILED`). `ticketId` on the chunk is how FR-15 maps hits back to tickets.

Replace the previous chunk set for that `ticketId` (delete-then-insert or equivalent) so stale vectors cannot be retrieved after an update.

### 4. Embedding

Each chunk’s **text** is sent to the embedding client. Model, dimensions, and hosting are open questions. Credentials from configuration only.

Failures: leave tracking `FAILED` with a generic reason; do not write a partial vector set for that ticket (all-or-nothing per ticket version).

### 5. Vector store

Upsert `(chunkId, vector, text, metadata)`. The store is not the system of record for ticket fields; metadata is a **snapshot** for filtering and citation. After success, tracking status `INDEXED` and store the `contentHash` / `ticket.updatedAt` used.

### Ask path (not ingestion)

```
question → (optional query embedding) → retrieve chunks
        → drop / reject below similarity threshold (threshold open)
        → if none remain: found=false, empty citations, no-match message (FR-16)
        → else: answer from chunk text + ticket reads; citations = distinct ticketIds (FR-14, FR-15)
```

Top-K and threshold are open; this spec does not assign numbers.

## Open questions (carried from `rules/rag-vector-store.md`)

1. Chunking strategy (stage 3 parameters; mapping chunk → ticket is already required via `ticketId`).
2. Embedding model (stage 4).
3. Top-K and similarity threshold (ask path).
