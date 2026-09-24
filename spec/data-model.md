# Data model

Relational source of truth for tickets/comments, ingestion-tracking entities, and the metadata that **must** appear on every embedded chunk. Aligns with `spec/requirements.md` and `spec/architecture.md`. Types are logical; physical DDL follows the same names and nullability.

**Spec delta vs FR-1:** `category` is required on `Ticket` so chunk metadata can be populated. Create/update APIs must accept and persist it when this model is implemented. Allowed **values** of category are not enumerated here (do not invent a taxonomy).

## Status enum

`TicketStatus` is the only ticket lifecycle enum. Transitions are enforced in the ticket module (FR-11, FR-12), not in ingestion.

| Value | Terminal | Allowed next |
| --- | --- | --- |
| `OPEN` | no | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | no | `RESOLVED`, `CANCELLED` |
| `RESOLVED` | no | `CLOSED` |
| `CLOSED` | yes | _(none)_ |
| `CANCELLED` | yes | _(none)_ |

Self-transitions are invalid. Stored as string (`OPEN`, …), never ordinal.

`TicketPriority`: `LOW`, `MEDIUM`, `HIGH`, `URGENT`.

`IngestionStatus`: `PENDING`, `INDEXED`, `STALE`, `FAILED` (ingestion tracking only; not a ticket status).

## Ticket

System of record for a support ticket. Table: `tickets`.

| Field | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | long, generated | no | Citation key (`ticketId` on chunks) |
| `title` | string, 1–200 | no | FR-1 / FR-5 |
| `description` | string, 1–10000 | no | FR-1 / FR-5 |
| `status` | `TicketStatus` | no | Default `OPEN` on create |
| `priority` | `TicketPriority` | no | Copied onto every chunk |
| `assignee` | string, ≤120 | yes | Null if unassigned; chunk metadata still includes the field (null/empty allowed) |
| `category` | string, 1–64 | no | Required for ingestion; copied onto every chunk |
| `createdAt` | instant UTC | no | Set on create |
| `updatedAt` | instant UTC | no | Bumped on update, transition, comment |

**Invariants:** Status changes only via `transitionTo` (FR-11). Comments are not stored as JSON on this row.

## Comment

Belongs to exactly one ticket. Table: `comments`.

| Field | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | long, generated | no | |
| `ticketId` | long, FK → `tickets.id` | no | Cascade with ticket if a ticket is removed |
| `author` | string, 1–120 | no | FR-6 |
| `body` | string, 1–10000 | no | FR-6 |
| `createdAt` | instant UTC | no | Immutable after insert |

**Invariants:** Ordered by `createdAt` ascending on ticket detail (FR-3). Adding a comment bumps parent `Ticket.updatedAt` and marks ingestion `STALE`.

## Ingestion-tracking entities

Derived from tickets; never used as the source of truth for title/status/etc.

### KnowledgeDocument

One current knowledge document per ticket (the canonical text + metadata snapshot used to produce chunks). Table: `knowledge_documents`.

| Field | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | long, generated | no | |
| `ticketId` | long, unique, FK | no | 1:1 with ticket |
| `contentHash` | string | no | Hash of canonical body + required metadata |
| `snapshotStatus` | `TicketStatus` | no | Status at last successful document build |
| `snapshotPriority` | `TicketPriority` | no | |
| `snapshotAssignee` | string | yes | Mirrors ticket at build time |
| `snapshotCategory` | string | no | |
| `canonicalText` | text | no | Deterministic document body (pre-chunk) |
| `updatedAt` | instant UTC | no | When this snapshot was last written |

### IngestionRecord

Tracks pipeline progress for that ticket. Table: `ingestion_records`.

| Field | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | long, generated | no | |
| `ticketId` | long, unique, FK | no | 1:1 with ticket |
| `status` | `IngestionStatus` | no | See below |
| `indexedContentHash` | string | yes | Hash last successfully upserted |
| `lastAttemptAt` | instant UTC | yes | |
| `lastSuccessAt` | instant UTC | yes | |
| `failureReason` | string | yes | Generic, no stack traces or secrets |

| `IngestionStatus` | Meaning |
| --- | --- |
| `PENDING` | Ticket exists; not yet indexed |
| `STALE` | Ticket/comments changed after `indexedContentHash` |
| `INDEXED` | Vector store matches this hash |
| `FAILED` | Last attempt did not complete an all-or-nothing upsert |

Create ticket → `PENDING`. Ticket or comment change after `INDEXED` → `STALE`. Successful pipeline → `INDEXED`. Failed embed/upsert → `FAILED` (retry leaves it `FAILED` or `STALE` until success). Ask must not write this table.

### ChunkRecord (relational, optional but required if we audit citations)

Local registry of what was sent to the vector store, so a hit’s `chunkId` maps to `ticketId` if the store is unavailable. Table: `chunk_records`.

| Field | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | string/UUID | no | Same id upserted to the vector store |
| `ticketId` | long, FK | no | |
| `documentId` | long, FK | no | Parent knowledge document |
| `ordinal` | int ≥ 0 | no | Order within the document |
| `text` | text | no | Exact embedded string |
| `ticketIdMeta` | long | no | Denormalized; must equal `ticketId` |
| `status` | `TicketStatus` | no | Snapshot on the chunk |
| `priority` | `TicketPriority` | no | Snapshot |
| `assignee` | string | yes | Snapshot |
| `category` | string | no | Snapshot |

On re-index, delete all `chunk_records` (and vectors) for `ticketId` before inserting the new set.

## Required metadata on every embedded chunk

The vector-store payload for **each** chunk includes these fields. Ingestion **must not** upsert a chunk that omits any of them. `assignee` may be null (unassigned); the key must still be present.

| Metadata field | Source | Purpose |
| --- | --- | --- |
| `ticketId` | `Ticket.id` | FR-15 citation; never inferred |
| `status` | `Ticket.status` at document build | Filter / display; snapshot, not live |
| `priority` | `Ticket.priority` at build | Filter / display |
| `assignee` | `Ticket.assignee` at build | Filter / display; null if unassigned |
| `category` | `Ticket.category` at build | Filter / display |

Chunk **text** is the only embedded content. Metadata is stored alongside the vector (not reconstructed from the vector). If ticket fields change later, chunks are stale until ingestion runs; ask citations still **verify** `title`/`status` from the live `Ticket` row (FR-15).

## Relationships

```
Ticket 1 ───< Comment
Ticket 1 ─── 1 KnowledgeDocument
Ticket 1 ─── 1 IngestionRecord
KnowledgeDocument 1 ───< ChunkRecord
```

Ask retrieval returns `chunkId`s → `ticketId` (from chunk metadata) → live `Ticket` for citation `title` and `status`. If live ticket is missing, drop that hit (do not cite a ghost id).
