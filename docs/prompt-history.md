# Prompt history

One mistake the assistant made that was caught and fixed during development.

## “Summarize ticket #4” answered with ticket 1

**What was asked.** In the ticket AI assistant: “Summarize ticket #4”. Ticket 4 was “500 error on chat” (HIGH, assignee Sam, category “Chat bot”). Ticket 1 was “SMTP password reset fails”.

**What went wrong.** The answer and the citation were for ticket 1. The question named an id, and the reply still described a different ticket.

**Why.** `AskService` always embedded the question and ran similarity search. The hash embedder does not treat “ticket #4” as a lookup by id, so the nearest chunks belonged to ticket 1. The language model was only allowed to use those chunks, and it summarized the wrong ticket.

**How it was fixed.** A question that matches `ticket` plus a number (optional `id` and `#`) no longer searches. `TicketIdMentions` collects those ids. `AskService` loads each one with `findByIdWithComments` and builds the prompt from that ticket only: title, status, priority, assignee, category, description, and comments. Citations are those ids. If every named id is missing, the response is `No relevant tickets were found.` and the model is not called. Questions that do not name an id still use similarity search.

**Check.** “Summarize ticket #4” then returned `found: true` with a single citation, ticket 4, and the facts from that row.
