# RAG & vector store (ticket QA)

Apply when changing the QA endpoint, retrieval, embeddings, or vector storage.

## Current state

- `POST /api/v1/qa` → `TicketQaService.answer` does **lexical** retrieval (keyword scoring over title, description, assignee, status, priority, comments). No embeddings or vector store exist yet.
- The response contract is fixed regardless of retrieval technology: `TicketQaResponse(question, found, answer, citations)` with `TicketCitation(ticketId, title, status)`.

## Non-negotiable behavior (product requirements)

These hold for the current keyword implementation and any future vector retrieval:

- **Ground strictly in real ticket data.** Every claim in `answer` must come from stored tickets/comments. Never fabricate.
- **Cite the tickets used.** Every produced answer includes `citations`; an answer without citations is a bug.
- **No match → say so.** When nothing relevant is found: `found=false`, empty `citations`, and an explicit "no relevant tickets" message. Retrieval changes must not weaken this.
- **Read-only.** QA never mutates tickets or comments.

## Architecture conventions

- Keep retrieval behind an interface in `application` (e.g. `TicketRetriever`); `TicketQaService` depends on the interface, not on a concrete store. Keep the keyword implementation as a fallback until vector retrieval is proven.
- Vector store / embedding client code lives in `infrastructure` (same layer rule as repositories). No SDK types in `api` or DTOs.
- Retrieval results must carry ticket identity (id, title, status), not just text, so citations map back to real tickets.
- Indexing is a separate write path from `POST /qa`; do not embed or upsert inside the QA request thread.
- Provider credentials and endpoints come from configuration/environment. Never hardcode API keys, model endpoints, or tokens.

```java
// BAD: inventing retrieval parameters before spec analysis
private static final int TOP_K = 5;            // not decided
private static final double MIN_SCORE = 0.78;  // not decided
private static final String MODEL = "text-embedding-3-small"; // not decided

// GOOD: keep the contract stable; resolve retrieval parameters in spec analysis first
public TicketQaResponse answer(TicketQaRequest request) { ... }
```

## OPEN QUESTIONS — resolve during spec analysis

Do **not** pick defaults in code, config, or tests until each is decided. When a question is resolved, replace it here with the decision and rationale, and add tests per `rules/testing.md`.

1. **Chunking strategy** — What is embedded: whole ticket, per-field, per-comment, or windows? Chunk size/overlap? How are comments grouped with their ticket? The decision must state how a chunk maps back to a ticket for citation.
2. **Embedding model** — Provider and model, dimensions, external API vs self-hosted, cost/latency constraints, and how the choice is configured (no hardcoded model names or keys).
3. **Top-K and similarity threshold** — Candidate count, the cutoff for "relevant", and behavior below threshold (must stay `found=false`, no fabrication). The decision must include the evaluation method: which questions were run and how relevance was judged.

## Testing

- Until the open questions are resolved, QA tests stay at the service level against the fixed contract — see `TicketQaServiceTest` (`found`, citations, no-fabrication).
- Whatever retrieval is chosen, tests must cover: citation correctness, no-match behavior, and threshold boundary behavior (once a threshold exists). Mock the retriever interface in unit tests; never call a live embedding API from tests.
