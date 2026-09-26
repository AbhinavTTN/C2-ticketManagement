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

There are 5 states, so 25 ordered pairs. Five are valid (above). The other **20** must be rejected: HTTP `409`, `code=INVALID_STATUS_TRANSITION`, stored status unchanged. Self-transitions are invalid.

| # | From | To | Why rejected |
| --- | --- | --- | --- |
| 1 | `OPEN` | `OPEN` | self-transition |
| 2 | `OPEN` | `RESOLVED` | skips `IN_PROGRESS` |
| 3 | `OPEN` | `CLOSED` | skips `IN_PROGRESS` and `RESOLVED` |
| 4 | `IN_PROGRESS` | `OPEN` | no reverse path |
| 5 | `IN_PROGRESS` | `IN_PROGRESS` | self-transition |
| 6 | `IN_PROGRESS` | `CLOSED` | skips `RESOLVED` |
| 7 | `RESOLVED` | `OPEN` | required example; no reverse path |
| 8 | `RESOLVED` | `IN_PROGRESS` | no reverse path |
| 9 | `RESOLVED` | `RESOLVED` | self-transition |
| 10 | `RESOLVED` | `CANCELLED` | cancel only from `OPEN` or `IN_PROGRESS` |
| 11 | `CLOSED` | `OPEN` | required example; terminal |
| 12 | `CLOSED` | `IN_PROGRESS` | terminal |
| 13 | `CLOSED` | `RESOLVED` | terminal |
| 14 | `CLOSED` | `CLOSED` | terminal; self-transition |
| 15 | `CLOSED` | `CANCELLED` | terminal |
| 16 | `CANCELLED` | `OPEN` | required example; terminal |
| 17 | `CANCELLED` | `IN_PROGRESS` | terminal |
| 18 | `CANCELLED` | `RESOLVED` | terminal |
| 19 | `CANCELLED` | `CLOSED` | terminal; close only from `RESOLVED` |
| 20 | `CANCELLED` | `CANCELLED` | terminal; self-transition |

Any new `TicketStatus` value is invalid until it is added to the valid table and this list is regenerated in the same change.

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
