package com.company.ticketmanagement.ask.application;

import java.util.ArrayList;
import java.util.List;

import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;

final class TicketExcerpts {

    private TicketExcerpts() {
    }

    static List<RetrievedChunk> of(long ticketId, Ticket ticket) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        chunks.add(new RetrievedChunk(ticketId, facts(ticket), 1d));
        for (String paragraph : paragraphs(ticket.getDescription())) {
            chunks.add(new RetrievedChunk(ticketId, section(ticket, "description", null, paragraph), 1d));
        }
        for (Comment comment : ticket.getComments()) {
            for (String paragraph : paragraphs(comment.getBody())) {
                chunks.add(new RetrievedChunk(ticketId, section(ticket, "comment", comment.getAuthor(), paragraph), 1d));
            }
        }
        return List.copyOf(chunks);
    }

    private static String facts(Ticket ticket) {
        String assignee = ticket.getAssignee() == null || ticket.getAssignee().isBlank()
                ? "unassigned"
                : ticket.getAssignee();
        return """
                Title: %s
                Status: %s
                Priority: %s
                Assignee: %s
                Category: %s
                """.formatted(
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                assignee,
                ticket.getCategory());
    }

    private static String section(Ticket ticket, String section, String author, String body) {
        StringBuilder text = new StringBuilder();
        text.append("Title: ").append(ticket.getTitle()).append('\n');
        text.append("Status: ").append(ticket.getStatus()).append('\n');
        text.append("Section: ").append(section).append('\n');
        if (author != null) {
            text.append("Author: ").append(author).append('\n');
        }
        text.append('\n').append(body);
        return text.toString();
    }

    private static List<String> paragraphs(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> paragraphs = new ArrayList<>();
        for (String part : normalized.split("\\n\\s*\\n")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return List.copyOf(paragraphs);
    }
}
