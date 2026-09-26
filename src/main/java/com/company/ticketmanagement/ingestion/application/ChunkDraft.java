package com.company.ticketmanagement.ingestion.application;

import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

public record ChunkDraft(
        int ordinal,
        String text,
        long ticketId,
        TicketStatus status,
        TicketPriority priority,
        String assignee,
        String category
) {
    public ChunkDraft {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Chunk text is required.");
        }
        if (status == null || priority == null || category == null || category.isBlank()) {
            throw new IllegalArgumentException(
                    "Chunk is missing ticketId, status, priority, or category.");
        }
    }
}
