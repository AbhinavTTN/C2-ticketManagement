# Evaluation strategy — retrieval and grounding

How to judge whether `POST /api/v1/qa` retrieves the right tickets and whether the answer stays inside those tickets (FR-14–FR-17). This does **not** choose an embedding model, top-K, or similarity threshold. Those stay open (`rules/rag-vector-store.md`). The checks below are how a candidate setting is accepted or rejected.

Chunking is paragraph/section based (`spec/rag-ingestion.md`). There is no `resolutionNotes` column; a “fix” lives in a comment or in the description.

## What is judged

Two separate scores. Do not collapse them into one “the answer looked fine.”

| Score | Question it answers | Pass unit |
| --- | --- | --- |
| Retrieval | Did the retriever surface the tickets that actually contain the answer? | Set of `ticketId`s |
| Grounding | Did `answer` and `citations` stay inside those tickets, with no invented facts? | Claims vs stored fields |

Wording of `answer` is **not** compared to a golden paragraph. Paraphrase is allowed when the meaning is still in the cited ticket (`commands/review-rag-output.md`). Ticket IDs, `found`, and a few anchor phrases are exact.

## Fixed corpus

Evaluation uses a **fixture corpus**, not production data. IDs below are the fixture ids. Rebuild the index from these rows before a run. Distractors exist so a lucky full-scan is visible.

| `ticketId` | Title | Description | Status | Comment (author: body) | Role |
| --- | --- | --- | --- | --- | --- |
| 101 | Cannot reset password | Reset email never arrives | `OPEN` | Sam: Checked SMTP logs | password / SMTP |
| 102 | SSO outage | Okta login failing in EU region | `IN_PROGRESS` | — | Okta / EU |
| 103 | VPN timeout | Office VPN drops hourly | `OPEN` | — | VPN |
| 104 | Printer jam | Floor 3 printer jammed | `OPEN` | — | distractor |
| 105 | Email bounce | Outbound mail is bouncing for billing | `RESOLVED` | Jordan: SPF record added | resolution lives in the comment |

Assignee, priority, and category may be any valid values; they are not the gold labels below. Do not add other tickets to this corpus without adding them to the expected sets.

## Sample questions and expected ticket IDs

`expectedTicketIds` is the complete gold set. Order does not matter. An empty set means no ticket is relevant.

| ID | Question | `expectedTicketIds` | `found` | Why this case exists |
| --- | --- | --- | --- | --- |
| Q1 | What do we know about password reset? | 101 | true | Keyword in title and description; 104 must not be cited |
| Q2 | Who checked the SMTP logs? | 101 | true | Answer is only in a comment, not the description |
| Q3 | What is going on with Okta in the EU? | 102 | true | Paraphrase of the description; no shared tokens with the title required |
| Q4 | Summarize ticket #103 | 103 | true | Explicit id; must load that ticket even if other text is weak |
| Q5 | What fixed the billing email bounce? | 105 | true | Resolution is the comment “SPF record added”, not a separate notes field |
| Q6 | Which tickets mention VPN or a password reset? | 101, 103 | true | Two gold ids; both must be cited; 104 must not |
| Q7 | What is the status of the lunar rover? | _(none)_ | false | No lexical or semantic overlap with any fixture |
| Q8 | Summarize ticket #999999 | _(none)_ | false | Named id is not in the corpus; do not invent a ticket |

Retrieval pass for one question:

- Every id in `expectedTicketIds` appears in the retrieved chunk metadata (recall = 1 on this set).
- Every cited `ticketId` is in `expectedTicketIds` (precision = 1 on this set). Extra citations are a failure, not a warning, on this corpus.
- If `expectedTicketIds` is empty: the retriever returns no chunks above the similarity cutoff (once a cutoff exists). Until a cutoff is chosen, an empty gold set still requires `found=false` and no citations in the API response.

Report, do not treat as a pass/fail number yet: the rank of the first gold id. That rank is the evidence used later to pick top-K. Do not freeze a K in this document.

A run passes retrieval only when **all** of Q1–Q8 pass. One miss fails the candidate (model, threshold, or chunking change).

## How grounding failures are detected

Run these after retrieval, on the API response (`question`, `found`, `answer`, `citations`). A grounding failure is any item below. Detection is automatic on this corpus; `commands/review-rag-output.md` is the same checklist for answers outside the fixture.

### Structural (always automatic)

| Failure | Detection |
| --- | --- |
| Answer with no citation | `found=true` and `citations` is empty |
| No-match that still cites | `found=false` and `citations` is non-empty |
| Mixed message | `found=true` and the answer contains “No relevant tickets were found” |
| False no-match | Q1–Q6 return `found=false` |
| False match | Q7 or Q8 return `found=true` |
| Ghost citation | A cited `ticketId` is not in the fixture table |
| Stale citation label | Cited `title` or `status` ≠ the fixture row |
| Missing gold citation | An id in `expectedTicketIds` is absent from `citations` |
| Distractor cited | 104 is cited on any question, or any id outside the gold set is cited |

### Claim check (automatic on the fixture)

Split `answer` into sentences. For Q1–Q6 each sentence must be supported by the gold tickets’ title, description, or comment body:

- **Anchor required.** The answer must contain the anchor for that question (substring, case-insensitive). The anchor is copied from stored text, so a hit means the answer used real ticket data.

| Question | Anchor that must appear | Phrase that must not appear |
| --- | --- | --- |
| Q1 | `Reset email never arrives` | `lunar` |
| Q2 | `Checked SMTP logs` | `printer` |
| Q3 | `Okta` | `VPN` |
| Q4 | `Office VPN drops hourly` | `Okta` |
| Q5 | `SPF record added` | `password` |
| Q6 | both `password` and `VPN` | `printer` |
| Q7, Q8 | `No relevant tickets were found` | any fixture title (`Cannot reset password`, `SSO outage`, `VPN timeout`, `Printer jam`, `Email bounce`) |

- **Unsupported sentence.** If a sentence does not overlap the gold tickets’ stored text (no shared content word of length ≥ 4 outside a fixed stop list) it is a grounding failure. This catches invented causes, ETAs, and fixes. It will also flag some harmless paraphrases; those are reviewed with `commands/review-rag-output.md`, they do not silently pass.
- **Merged facts.** A sentence that states a field from ticket A (assignee, status, comment) as if it belonged to ticket B is a grounding failure. On this corpus, check that “SMTP” is never tied to 102–105 and “SPF” is never tied to 101–104.

Q7 and Q8 must not add a second sentence that names a cause, status, or ticket.

### What is not automated

A human (or the review command) still reads any sentence the overlap check flagged, to separate a true invention from a fair paraphrase. That review does not override a failed anchor, a wrong id set, or a wrong `found` flag. Those stay failures.

## When this runs

- After any change to chunking, the embedding model, top-K, or the similarity threshold.
- The corpus and the Q1–Q8 table are the evaluation method those open decisions must cite.
- Do not call a live embedding API from unit tests. This evaluation is a separate run against the fixture index (see `spec/test-strategy.md`).
