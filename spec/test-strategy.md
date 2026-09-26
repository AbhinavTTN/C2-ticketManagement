# Test strategy

Two kinds of behavior, tested differently. Deterministic logic either matches the spec or it fails the build. Probabilistic retrieval is scored on ticket-id sets and anchors, not on exact answer wording.

Conventions for unit and integration tests stay in `rules/testing.md`. The state machine is `spec/state-machine.md`. Retrieval and grounding cases are `spec/evaluation-strategy.md`.

## Deterministic — ticket logic

These tests must pass on every change. A flake is a defect, not an acceptable variance. No network, no embedding API.

### State machine (required)

Cover every `(from, to)` pair of `TicketStatus`, including `from == to`.

| Case | Assert |
| --- | --- |
| Valid pair (`OPEN→IN_PROGRESS`, `OPEN→CANCELLED`, `IN_PROGRESS→RESOLVED`, `IN_PROGRESS→CANCELLED`, `RESOLVED→CLOSED`) | Status becomes the target on the entity and on the response |
| Each of the 20 invalid pairs in `spec/state-machine.md` | `TicketStateConflictException` (HTTP 409 at the API), status unchanged, repository `save` not called with a mutated ticket |
| Completeness | `validTransitions()` ∪ `invalidTransitions()` equals the full 5×5 matrix; a new enum value fails this test until both sources are updated |

Implement with `@ParameterizedTest` in `TicketServiceTest`. One API integration test samples a single 409 (for example `OPEN→CLOSED`). That sample does not replace the unit matrix.

### Other deterministic ticket tests

| Area | Assert |
| --- | --- |
| Create | Persisted status is `OPEN`; blank required fields are 400 and nothing is stored |
| Update | Title, description, priority, assignee change; status does not |
| Comment | Stored on that ticket; unknown id is 404 |
| List / search / filter | `q` and `status` AND together; empty page is 200, not 404 |
| Not found | Unknown id on get, update, transition, comment is 404 `TICKET_NOT_FOUND` |

Repository and API tests use PostgreSQL in Testcontainers, never H2.

### QA with a fake retriever (still deterministic)

`TicketQaService` unit tests mock the retriever. They do not prove that search finds the right tickets. They prove the contract once a hit list is given:

- Hits present → `found=true`, citations are exactly those ticket ids, answer text is taken from the supplied ticket fields (anchor phrases from the fixture).
- Empty hits → `found=false`, empty citations, answer contains “No relevant tickets were found” and no fixture title.
- The service does not call `save` or otherwise mutate tickets.

## Probabilistic — retrieval quality

Embedding search can change rank when the model or threshold changes. Tests must not expect a fixed sentence from the model. They expect the **sets and anchors** in `spec/evaluation-strategy.md`.

| Property | Deterministic suite | Retrieval evaluation |
| --- | --- | --- |
| Input | Mocks or one database | Frozen fixture tickets 101–105 |
| Oracle | Status code, exception type, field equality | Gold `ticketId` set per question Q1–Q8 |
| Wording | Exact where the service formats it | Anchor substring only |
| Failure | Build fails | Candidate model / threshold / chunking is rejected |
| Live embedding API | Never | Only in this evaluation run |

### What the evaluation run checks

1. Index the fixture with the chunking in `spec/rag-ingestion.md`.
2. Ask Q1–Q8.
3. Retrieval: gold ids ⊆ retrieved ids, and cited ids = gold ids. Empty gold → `found=false` and no citations.
4. Grounding: structural checks and anchor / forbidden-phrase table in `spec/evaluation-strategy.md`.
5. Record the rank of the first gold id for each of Q1–Q6. That table is the input for choosing top-K later. The run does not invent a K.

All eight questions must pass. There is no partial credit and no “flaky retry” that turns a miss into a pass. If the same index and same question fail on one run and pass on the next, treat the retriever as non-deterministic and fail the run until it is stable on this corpus.

### What stays out of the unit-test build

Do not put Q1–Q8 behind `@SpringBootTest` in the default unit run if that would call a paid or remote embedding API. Keep them as a separate evaluation task that runs when chunking, the model, or the threshold changes. Mocked QA tests in the unit run are the regression net for grounding **given** a hit list.

## How the two layers meet

```
state machine, validation, CRUD     → JUnit, every build, exact
QA service given a fake hit list    → JUnit, every build, exact ids and anchors
retriever on the fixture corpus     → evaluation run, id sets and anchors, not prose
```

A state-machine change is incomplete without the exhaustive matrix. A retrieval change is incomplete without a Q1–Q8 report. Neither suite replaces the other: a perfect transition test says nothing about invented answers, and a passing eval says nothing about `CLOSED→OPEN`.
