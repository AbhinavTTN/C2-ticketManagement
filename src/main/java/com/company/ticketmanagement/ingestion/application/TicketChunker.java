package com.company.ticketmanagement.ingestion.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;

@Component
public class TicketChunker {

    public List<ChunkDraft> chunk(Ticket ticket) {
        List<ChunkDraft> chunks = new ArrayList<>();
        int ordinal = 0;
        for (String paragraph : paragraphs(ticket.getDescription())) {
            chunks.add(draft(ticket, ordinal++, "description", null, paragraph));
        }
        List<Comment> comments = ticket.getComments().stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(comment -> comment.getId() == null ? Long.MAX_VALUE : comment.getId()))
                .toList();
        for (Comment comment : comments) {
            for (String paragraph : paragraphs(comment.getBody())) {
                chunks.add(draft(ticket, ordinal++, "comment", comment.getAuthor(), paragraph));
            }
        }
        return List.copyOf(chunks);
    }

    private static ChunkDraft draft(Ticket ticket, int ordinal, String section, String author, String body) {
        return new ChunkDraft(
                ordinal,
                header(ticket.getTitle(), section, author) + body,
                ticket.getId(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getCategory());
    }

    static String header(String title, String section, String author) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(title).append('\n');
        sb.append("Section: ").append(section).append('\n');
        if (author != null) {
            sb.append("Author: ").append(author).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    static List<String> paragraphs(String text) {
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
