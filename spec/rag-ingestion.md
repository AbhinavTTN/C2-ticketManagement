# RAG ingestion — chunking recommendation

How ticket text is split before embedding. Pipeline stages and metadata requirements stay as in `spec/architecture.md` and `spec/data-model.md`. This document **decides the split unit**. It does not pick an embedding model, top-K, or similarity threshold (`rules/rag-vector-store.md` questions 2 and 3 remain open).

## What ticket text actually is

There is no `resolutionNotes` column. Text that an ask answer may quote comes from three stored places:

| Section | Where it lives | Shape |
| --- | --- | --- |
| Problem statement | `Ticket.description` | One field, 1–10000 characters. Agents often write one or more paragraphs separated by blank lines. Sometimes a single block with no breaks. |
| Work log | `Comment` rows | One row per update: `author`, `body` (1–10000), `createdAt`. Ordered oldest first. Each comment is a separate authored event, not a continuation of the previous sentence. |
| Resolution notes | Not a field | Whatever was written when the ticket moved toward `RESOLVED` / `CLOSED`. In this model that is comment bodies (and sometimes an edited description). The latest comments are the usual place a fix, workaround, or “won’t fix” is recorded. |

A knowledge document is therefore a **sequence of labeled sections**, not one article:

1. Title (short; not a chunk by itself — it is prepended as a header on every chunk so a hit still names the ticket).
2. Description, as one or more paragraphs.
3. Each comment, in `createdAt` order, labeled with author and time.

Every chunk still carries `ticketId`, `status`, `priority`, `assignee`, and `category` (`spec/data-model.md`). `ticketId` is how a hit becomes a citation (FR-15).

## Option A — paragraph / section chunking

Split on the structure above:

- Description: split on blank lines (one or more empty lines). Each non-empty paragraph is one chunk. A description with no blank lines is one chunk.
- Each comment is its own chunk. If that comment body itself contains blank-line paragraphs, split those into separate chunks, still labeled with the same comment id and author.
- Do not merge a description paragraph with a comment, and do not merge two comments.
- Chunk text includes a short header: ticket title, section name (`description` or `comment`), and for comments the author. The header is stored text, not a substitute for metadata.

## Option B — fixed-size chunking

Concatenate description + comments + any resolution text into one string and cut it into windows of a fixed character or token length, usually with overlap so a cut does not drop a sentence.

The window length and overlap are parameters. This option needs those numbers before it can be implemented; they are not chosen here.

## Comparison

| | Paragraph / section | Fixed-size windows |
| --- | --- | --- |
| Respects comment boundaries | Yes. One author’s update stays intact. | No. A window can end mid-comment and start the next author’s text. |
| Resolution vs problem | A question about the fix can hit a later comment without dragging the whole description into the same vector. | A window often mixes the problem statement and the fix, so similarity is diluted and the quoted span is ambiguous. |
| Short tickets | A one-line comment stays one chunk. Most tickets are a short description plus a handful of comments, so chunk count stays small. | The same short ticket is either one undersized window or is padded by neighboring sections that do not belong together. |
| Long description (up to 10000 characters) with no blank lines | Becomes one large chunk. That is the weak case. | Bounds the vector size. That is the strong case. |
| Mid-sentence cuts | No, except in the overflow fallback below. | Yes, including through error messages and steps that agents write as one paragraph. |
| Citation | Each chunk maps to `ticketId` plus an optional comment id, so the answer can quote a specific update. | Each chunk maps to `ticketId` only; the source comment is not stable because the window moves when an earlier comment is edited. |
| Re-index cost | Replacing chunks is stable: same paragraphs produce the same boundaries. | Editing one early comment shifts every later window, so the whole ticket’s vectors change even when later comments did not. |

## Recommendation

**Use paragraph / section chunking (option A).**

Ticket data is not a continuous document. Description, each comment, and the resolution (which lives in comments, not in its own column) are separate authored units. Fixed-size windows ignore that and glue one comment to the next, which makes it easy for an answer to mix two people’s words or to treat the problem statement as the fix. Paragraph boundaries match how this data is written and stored: blank lines inside a description, and a new row for each comment.

Fixed-size chunking is the better default for long homogeneous prose (articles, PDFs). It is the worse default here because the natural unit is already small and semantically closed.

**Overflow, still open:** if a single paragraph or comment exceeds what the chosen embedding model accepts, split only that oversized section into windows. The maximum length is not set in this document — it depends on the model (open question 2). Until that model is chosen, do not pick a character or token size, and do not apply fixed windows to sections that already fit.

**Not decided here:** embedding model, top-K, similarity threshold. Below-threshold retrieval must still return `found=false` with an explicit no-match message (FR-16).
