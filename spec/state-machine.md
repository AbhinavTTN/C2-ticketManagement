# Ticket status state machine

Formal transition table and the complete invalid set. Enforced by the ticket domain (`TicketStatus.allowedTransitions()` / `transitionTo`), per FR-11 and FR-12. Tests must cover every pair (valid and invalid) per `rules/testing.md`.

## States

| State | Terminal | Entered from |
| --- | --- | --- |
| `OPEN` | no | create only |
| `IN_PROGRESS` | no | `OPEN` |
| `RESOLVED` | no | `IN_PROGRESS` |
| `CLOSED` | yes | `RESOLVED` |
| `CANCELLED` | yes | `OPEN`, `IN_PROGRESS` |

New tickets are always `OPEN` (FR-1). There is no path back into `OPEN` after leaving it.

## Valid transitions (complete)

| From | To |
| --- | --- |
| `OPEN` | `IN_PROGRESS` |
| `OPEN` | `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED` |
| `IN_PROGRESS` | `CANCELLED` |
| `RESOLVED` | `CLOSED` |

Success: HTTP `200`, stored status becomes the target, `updatedAt` advances. Any pair not in this table is invalid.

## Invalid transitions (must be rejected)

All 20 pairs below return HTTP `409` with `code=INVALID_STATUS_TRANSITION`; stored status does not change. Self-transitions are invalid.

| From | Invalid targets |
| --- | --- |
| `OPEN` | `OPEN`, `RESOLVED`, `CLOSED` |
| `IN_PROGRESS` | `OPEN`, `IN_PROGRESS`, `CLOSED` |
| `RESOLVED` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CANCELLED` |
| `CLOSED` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` |
| `CANCELLED` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` |

Required examples (from the product brief): `CLOSED` → `OPEN`, `RESOLVED` → `OPEN`, `CANCELLED` → `OPEN` — all in the table above.

## Full matrix (✓ = allowed, ✗ = rejected)

| From \ To | `OPEN` | `IN_PROGRESS` | `RESOLVED` | `CLOSED` | `CANCELLED` |
| --- | --- | --- | --- | --- | --- |
| `OPEN` | ✗ | ✓ | ✗ | ✗ | ✓ |
| `IN_PROGRESS` | ✗ | ✗ | ✓ | ✗ | ✓ |
| `RESOLVED` | ✗ | ✗ | ✗ | ✓ | ✗ |
| `CLOSED` | ✗ | ✗ | ✗ | ✗ | ✗ |
| `CANCELLED` | ✗ | ✗ | ✗ | ✗ | ✗ |

## Rules

- Status changes **only** via `POST /api/v1/tickets/{id}/status` (see `spec/api-contract.md`). `PATCH` never changes status.
- Rejection is atomic: no partial update, no comment side effects, no save of a mutated entity.
- Unknown ticket id → `404 TICKET_NOT_FOUND` (not a transition error).
