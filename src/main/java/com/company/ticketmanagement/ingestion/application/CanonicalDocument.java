package com.company.ticketmanagement.ingestion.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;

public record CanonicalDocument(String text, String contentHash) {

    public static CanonicalDocument from(Ticket ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(ticket.getTitle()).append('\n');
        sb.append("Status: ").append(ticket.getStatus()).append('\n');
        sb.append("Priority: ").append(ticket.getPriority()).append('\n');
        sb.append("Assignee: ").append(ticket.getAssignee() == null ? "" : ticket.getAssignee()).append('\n');
        sb.append("Category: ").append(ticket.getCategory()).append('\n');
        sb.append("Description:\n").append(ticket.getDescription()).append('\n');
        ticket.getComments().stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(comment -> comment.getId() == null ? Long.MAX_VALUE : comment.getId()))
                .forEach(comment -> sb.append("Comment by ")
                        .append(comment.getAuthor())
                        .append(" at ")
                        .append(comment.getCreatedAt())
                        .append(":\n")
                        .append(comment.getBody())
                        .append('\n'));
        String text = sb.toString();
        return new CanonicalDocument(text, sha256(text));
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
