# UI flow

Screens for the support ticket app: list, detail, create/edit, and the AI Q&A panel. Behavior follows `spec/api-contract.md`, `spec/state-machine.md`, and FR-13–FR-17. No authentication, roles, or attachments.

Errors always come from the response body: show `title` and `detail`. When `fields` is present, highlight those inputs with the field message. Never show a stack trace, SQL, or the raw HTTP status alone. An `INTERNAL_ERROR` shows only the generic `detail`.

## Map

| Screen | Route | Primary API |
| --- | --- | --- |
| Ticket list | `/tickets` | `GET /api/v1/tickets` |
| Create | `/tickets/new` | `POST /api/v1/tickets` |
| Detail | `/tickets/{id}` | `GET /api/v1/tickets/{id}` |
| Edit | `/tickets/{id}/edit` | `PATCH /api/v1/tickets/{id}` |
| AI Q&A panel | overlay on list and detail | `POST /api/v1/qa` |

The panel does not navigate away from the current screen. Choosing a citation opens that ticket’s detail.

## Ticket list

**Enter.** Open `/tickets`. Request page `0`, size `20`, no `status`, no `q`. Sort is the server order (`updatedAt` descending).

**Show.** One row per item in `content`: title, status, priority, assignee (or “Unassigned” when null), category, `updatedAt`. Do not show description or comments on the row.

**Search.** A text field submits `q`. Blank input omits `q`. Results replace the list. The active page resets to `0`.

**Filter.** A status control offers All plus `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`. All omits `status`. Search and filter both apply (AND). Changing either resets to page `0`.

**Paging.** Show `page` (1-based in the UI; the request stays 0-based), `size`, and `totalElements`. Next is disabled on the last page. Previous is disabled on page 0. A page past the end shows an empty list with `totalElements` unchanged, not an error.

**Empty.** `content` empty and `totalElements` 0: “No tickets match.” This is not an error. Offer create. If a filter or keyword is set, also offer clear.

**Row.** Activating a row opens `/tickets/{id}`.

**Create.** A create action opens `/tickets/new`.

**Failure.** A failed list request shows `title` and `detail`. The previous rows stay visible if any were loaded. An invalid `status` or `page`/`size` (400) shows `detail` and does not clear the form values the user can fix.

## Detail

**Enter.** `GET /api/v1/tickets/{id}`.

**Show.** Title, description, status, priority, assignee, category, `createdAt`, `updatedAt`. Comments in list order (oldest first): author, body, `createdAt`. If there are no comments, show “No comments yet.”

**Status actions.** Render one control per value in `allowedTransitions` only. `CLOSED` and `CANCELLED` show no transition controls. The user cannot type a status. Choosing a control sends `POST /api/v1/tickets/{id}/status` with that status, then reloads the detail from the response.

**Invalid transition.** On 409, keep the current detail, leave status as it was, and show `detail` (for example “Cannot transition a ticket from OPEN to CLOSED.”). Do not offer that target again unless a later reload includes it in `allowedTransitions`.

**Comment.** Author and body fields. Submit `POST /api/v1/tickets/{id}/comments`. On 201, replace the detail with the response so the new comment and `updatedAt` appear. On 400, highlight `author` or `body` from `fields`.

**Edit.** An edit action opens `/tickets/{id}/edit`. It does not change status.

**Not found.** 404 shows `detail` and a link back to `/tickets`. Do not show an empty ticket form.

## Create form

**Fields.** Title (required), description (required), priority (required; `LOW`, `MEDIUM`, `HIGH`, `URGENT`), assignee (optional), category (required, 1–64 characters). No status field. The ticket will be `OPEN`.

**Submit.** `POST /api/v1/tickets`. Disable the submit control while the request is in flight.

**Success.** 201 opens `/tickets/{id}` for the new id.

**Validation.** 400: keep the typed values, focus the first field in `fields`, and show each field message next to that input. Also show `detail` once above the form.

**Cancel.** Return to `/tickets` without writing.

## Edit form

**Enter.** Load `GET /api/v1/tickets/{id}` and fill title, description, priority, assignee, and category. Show status as read-only text. The form does not send `status`.

**Submit.** `PATCH` with only the fields the user can edit. Blank assignee clears the assignee. Omitted unchanged fields may be sent with their current values.

**Success.** 200 returns to `/tickets/{id}` showing the updated ticket. Status and comments are unchanged by this save.

**Validation.** Same as create: `fields` on the inputs, `detail` above the form, values kept.

**Not found.** 404 shows `detail` and returns the user to the list. Do not save.

**Cancel.** Return to the detail without writing.

## AI Q&A panel

The panel is read-only. It has no create, edit, comment, or status controls. It calls only `POST /api/v1/qa`.

**Open.** A control on the list and on the detail opens the panel. The ticket screen underneath stays mounted. Closing the panel discards nothing on the ticket.

**Ask.** One question field (required, max 1000 characters) and a submit control. Empty or whitespace-only input does not call the API; show “Question is required.” On submit, disable the control until the response returns.

**Answer found.** `found=true`: show `answer`, then one citation row per item in `citations` — title, status, and `ticketId`. The list must be non-empty. Activating a citation opens `/tickets/{ticketId}`. If `found=true` and `citations` is empty, show “This answer did not cite a ticket” and do not present `answer` as reliable.

**Nothing found.** `found=false`: show the server `answer` (the no-relevant-tickets message) and no citation rows. Do not add a suggested ticket, a guessed cause, or a follow-up that implies a match.

**Error.** 400 highlights the question with `fields.question` or `detail`. 500 shows the generic `detail` only. A failed ask leaves the previous answer on screen if one existed, with the error above it.

**While a ticket is open.** The panel does not auto-fill the question with that ticket. The user may type “ticket #123” themselves. The panel does not refresh the ticket after an answer, because QA must not change ticket data.
