# Architecture

Target module layout and the RAG ingestion pipeline. Complements `spec/requirements.md` (what) and `spec/data-model.md` (entities). Layering inside each module follows `rules/java-springboot.md`. Retrieval/indexing rules in `rules/rag-vector-store.md` apply. **Chunking and the embedding model are decided below.** Top-K and the similarity threshold are configured (`app.ask.top-k`, `app.ask.similarity-threshold`) and have not been accepted by the Q1–Q8 evaluation.

## Principles

- Feature packages under `com.company.ticketmanagement`, each with `api` / `application` / `domain` / `dto` / `mapper` / `infrastructure` as needed. Shared exceptions stay in `common`.
- Ticket writes never call the embedding API. Ingestion is a **separate write path** from `POST /api/v1/qa`.
- The ask module is **read-only** against tickets/comments (FR-14–FR-17). It may read the vector store and the relational ticket rows for citation metadata.
- Vector-store and embedding SDK types stay in `infrastructure`. DTOs and controllers never import them.
- Ask retrieval is vector search over ingested chunks, except when the question names a ticket id. That path loads the named ticket only and does not search. Both paths stay bound to FR-14–FR-17.

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

**Must not:** Write tickets, comments, or ingestion rows; embed or upsert ticket text inside the request. Query-time embedding of the **question** uses the same client as ingestion. Indexing tickets does not.

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
- Body: title, description paragraphs, then each comment in `createdAt` order. The title is a header on every chunk, not its own chunk. There is no `resolutionNotes` column; a fix lives in a comment or in the description (`spec/rag-ingestion.md`).
- `contentHash` over that canonical body + metadata so the pipeline can skip embed when nothing changed.

Persist or replace the `KnowledgeDocument` row (see data model) with this snapshot **before** embedding.

### 3. Chunk

**Decision: paragraph / section chunking** (`TicketChunker`, `spec/rag-ingestion.md`).

- Description: split on blank lines. Each non-empty paragraph is one chunk. A description with no blank lines is one chunk.
- Each comment paragraph is its own chunk, in comment order. A comment with no blank lines is one chunk.
- Do not merge a description paragraph with a comment, and do not merge two comments.
- Chunk text starts with `Title`, `Section` (`description` or `comment`), and `Author` when the section is a comment. That header is stored text. It does not replace metadata.

Fixed-size windows were the other option: concatenate the ticket and cut overlapping character or token spans. They were rejected for this data.

| | Paragraph / section (chosen) | Fixed-size windows |
| --- | --- | --- |
| Quality | A comment stays one author's update. A question about the fix can hit a later comment without mixing it into the problem statement. | A window can end mid-comment and start the next author's text, so the quoted span is ambiguous. |
| Cost | Boundaries stay put when a later comment is added. Re-index replaces a stable set. Short tickets stay a handful of chunks. | Editing an early comment shifts every later window, so unchanged comments are re-embedded. |
| Latency | Chunk count tracks paragraphs and comments, which is small for a typical ticket. | Overlap multiplies vectors for the same short ticket. |
| Weak case | A description of up to 10000 characters with no blank lines is one large chunk. | Bounds that vector, at the cost of mid-sentence cuts. |

The hash embedder below accepts any length, so oversized sections are not split into windows.

**Invariant:** every chunk written to the vector store includes **all** required metadata: `ticketId`, `status`, `priority`, `assignee`, `category`. Missing any field → do not upsert that chunk (tracking `FAILED`). `ticketId` on the chunk is how FR-15 maps hits back to tickets.

Replace the previous chunk set for that `ticketId` (delete-then-insert or equivalent) so stale vectors cannot be retrieved after an update.

### 4. Embedding

**Decision: local deterministic hash embedder** (`HashEmbeddingClient`).

Each chunk’s text is folded character by character into a 32-float vector (`app.ingestion.embedding-dimensions`), then L2-normalized. The same string always yields the same vector. Empty text becomes a unit vector on the first component. Width matches `vector(32)` in `V2__ingestion_pgvector.sql`. Changing the width needs a new migration. No API key and no network call.

A hosted semantic embedding API and a local neural embedder were the other options. Neither is wired in.

| | Hash embedder (chosen) | Hosted semantic API | Local neural embedder |
| --- | --- | --- | --- |
| Cost | None. Runs in-process. | Per-token charge, plus a key to store and rotate. | No per-token bill. Uses local disk and memory for the model weights. |
| Latency | A loop over the chunk text. No round trip, so ingestion and query embedding stay off the network. | One HTTP call per chunk and per question. Ingestion of a ticket waits on that provider. | Local compute, slower than the hash and faster than a remote call when the model is already loaded. |
| Quality | Not semantic. Similar character mixes score as near even when the subjects differ. A review of live questions cited the SMTP and VPN tickets for payment failures and for an out-of-scope lunar-rover question. | Meaning-based similarity. That is the quality this index needs, and it was not selected because no candidate has passed Q1–Q8 (`spec/evaluation-strategy.md`). | Same quality goal as a hosted model, still unevaluated, and unit tests must not call a live model. |
| Tests | Deterministic. Unit and Testcontainers tests embed offline. | A live call in the default test run is forbidden (`spec/test-strategy.md`). | Same restriction. |

The hash embedder is the model the pipeline and the ask path use. It is a stand-in that keeps pgvector filled without pretending the vectors mean the same thing as a semantic model. Replacing it is a new `EmbeddingClient` plus a migration for the column width, then a Q1–Q8 run.

Failures: leave tracking `FAILED` with a generic reason; do not write a partial vector set for that ticket (all-or-nothing per ticket version).

### 5. Vector store

Upsert `(chunkId, vector, text, metadata)`. The store is not the system of record for ticket fields; metadata is a **snapshot** for filtering and citation. After success, tracking status `INDEXED` and store the `contentHash` / `ticket.updatedAt` used.

### Ask path (not ingestion)

```
question names a ticket id → load that ticket only (no vector search)
question otherwise → embed with the hash embedder → retrieve chunks
        → drop hits below the configured similarity threshold
        → if none remain: found=false, empty citations, no-match message (FR-16)
        → else: answer from chunk text + ticket reads; citations = distinct ticketIds (FR-14, FR-15)
```

Top-K and the threshold are configuration, not constants in Java. They are not closed by this document: the current numbers have not passed Q1–Q8.

## Closed and still open

1. Chunking strategy — closed. Paragraph / section, stage 3.
2. Embedding model — closed for this build. Local 32-dimension hash embedder, stage 4. A semantic model stays out until Q1–Q8 accepts it.
3. Top-K and similarity threshold — still open. Configured, not evaluated.
