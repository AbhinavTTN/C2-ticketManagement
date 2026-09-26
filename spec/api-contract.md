# API contract — tickets (v1)

HTTP contract for ticket CRUD, comments, search, and filter. Satisfies FR-1–FR-13. Error bodies follow `rules/api-standards.md` (RFC 7807 `ProblemDetail` + `code`). QA is out of scope here (`POST /api/v1/qa`).

**Conventions:** JSON camelCase; enums as strings; `Instant` as ISO-8601 UTC. Version in the URI. `category` is part of the ticket model (`spec/data-model.md`); it appears on responses and is accepted on create/update when the ingestion model is implemented.

## Endpoint summary

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `POST` | `/api/v1/tickets` | Create | `201` |
| `GET` | `/api/v1/tickets` | List, search, filter, paginate | `200` |
| `GET` | `/api/v1/tickets/{id}` | View detail | `200` |
| `PATCH` | `/api/v1/tickets/{id}` | Update fields | `200` |
| `POST` | `/api/v1/tickets/{id}/comments` | Add comment | `201` |
| `POST` | `/api/v1/tickets/{id}/status` | Transition status | `200` |

---

## Create — `POST /api/v1/tickets`

**Request**

```json
{
  "title": "Email bounce",
  "description": "Outbound mail is bouncing for billing",
  "priority": "HIGH",
  "assignee": "Riley",
  "category": "billing"
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `title` | yes | 1–200 chars after trim |
| `description` | yes | 1–10000 chars after trim |
| `priority` | yes | `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT` |
| `assignee` | no | ≤120 chars; blank treated as omitted |
| `category` | per data model | Required once `spec/data-model.md` is implemented; 1–64 chars |

**Response `201`** — `TicketResponse` (see schemas). `status` is `OPEN`; `comments` is empty; `allowedTransitions` is `["IN_PROGRESS","CANCELLED"]`.

**Errors:** `400 VALIDATION_FAILED` (missing/blank/too-long fields, bad enum, unreadable JSON).

## List — `GET /api/v1/tickets`

Search (FR-7) and filter (FR-8) are query parameters on this endpoint, not separate paths.

**Query params**

| Param | Default | Rules |
| --- | --- | --- |
| `status` | omitted | One `TicketStatus`; invalid value → `400 VALIDATION_FAILED` |
| `q` | omitted | Keyword; matches title, description, comment author/body (case-insensitive). Blank treated as omitted |
| `page` | `0` | 0-based; negative → `400` |
| `size` | `20` | 1–100; out of range → `400` |

### Search

`q` is the keyword search. A ticket matches when the keyword appears, case-insensitive, in `title`, `description`, or any comment `author` or `body`. Blank `q` is omitted (no keyword constraint). No matches → `200` and an empty page.

### Filter

`status` filters to one `TicketStatus`. An unrecognized value → `400 VALIDATION_FAILED`. Omitted `status` means all statuses.

`status` and `q` combine with AND. Both apply before paging. Default sort: `updatedAt` descending.

**Response `200`** — page envelope (never a bare array):

```json
{
  "content": [
    {
      "id": 1,
      "title": "Email bounce",
      "status": "OPEN",
      "priority": "HIGH",
      "assignee": "Riley",
      "category": "billing",
      "updatedAt": "2026-09-24T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Empty result: `200` with `"content": []`, `totalElements: 0`, `totalPages: 0`.

## View detail — `GET /api/v1/tickets/{id}`

**Response `200`** — `TicketResponse` with comments (oldest first) and `allowedTransitions` for the current status.

**Errors:** `404 TICKET_NOT_FOUND`.

## Update — `PATCH /api/v1/tickets/{id}`

Partial update of `title`, `description`, `priority`, `assignee`, `category` only. Omitted fields are unchanged. Blank `assignee` clears it. **Status is not updatable here.**

**Request** (any subset):

```json
{
  "title": "Email bounce — billing",
  "description": "SPF record missing",
  "priority": "URGENT",
  "assignee": "Jordan",
  "category": "billing"
}
```

**Response `200`** — updated `TicketResponse`; `updatedAt` advanced.

**Errors:** `404 TICKET_NOT_FOUND`; `400 VALIDATION_FAILED` (e.g. title over 200 chars, invalid priority).

## Add comment — `POST /api/v1/tickets/{id}/comments`

**Request**

```json
{ "author": "Jordan", "body": "SPF fix scheduled for tonight" }
```

| Field | Required | Rules |
| --- | --- | --- |
| `author` | yes | non-blank, ≤120 chars |
| `body` | yes | non-blank, ≤10000 chars |

**Response `201`** — `TicketResponse` including the new comment (and refreshed `updatedAt`).

**Errors:** `404 TICKET_NOT_FOUND`; `400 VALIDATION_FAILED`.

## Transition status — `POST /api/v1/tickets/{id}/status`

**Request**

```json
{ "status": "IN_PROGRESS" }
```

**Response `200`** — `TicketResponse` with new `status` and updated `allowedTransitions`.

**Errors:** `404 TICKET_NOT_FOUND`; `400 VALIDATION_FAILED` (missing/invalid status value); `409 INVALID_STATUS_TRANSITION` for any pair not in `spec/state-machine.md` (including self-transitions).

---

## Schemas

### TicketResponse

```json
{
  "id": 1,
  "title": "Email bounce",
  "description": "Outbound mail is bouncing for billing",
  "status": "OPEN",
  "priority": "HIGH",
  "assignee": "Riley",
  "category": "billing",
  "createdAt": "2026-09-24T09:00:00Z",
  "updatedAt": "2026-09-24T10:00:00Z",
  "comments": [
    { "id": 10, "author": "Jordan", "body": "SPF fix scheduled", "createdAt": "2026-09-24T09:30:00Z" }
  ],
  "allowedTransitions": ["IN_PROGRESS", "CANCELLED"]
}
```

`assignee` may be `null`. `allowedTransitions` is `[]` for `CLOSED` and `CANCELLED`.

### TicketSummaryResponse

Subset used in list `content`: `id`, `title`, `status`, `priority`, `assignee`, `category`, `updatedAt`. No comments.

### CommentResponse

`id`, `author`, `body`, `createdAt`.

### Error body

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed. Correct the highlighted fields and try again.",
  "code": "VALIDATION_FAILED",
  "fields": { "title": "Title is required." }
}
```

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Bean Validation, unreadable JSON, bad enum/param, bad page/size |
| 404 | `TICKET_NOT_FOUND` | Unknown ticket id |
| 409 | `INVALID_STATUS_TRANSITION` | Transition not allowed |
| 500 | `INTERNAL_ERROR` | Unexpected; generic message only |

## Notes

- No `DELETE` in v1; cancellation is `POST .../status` with `CANCELLED`.
- List pagination is the v1 contract for new work; if legacy clients depend on a bare array, versioning rules in `rules/api-standards.md` apply.
