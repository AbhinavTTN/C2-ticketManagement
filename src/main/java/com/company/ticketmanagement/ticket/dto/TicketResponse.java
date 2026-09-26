package com.company.ticketmanagement.ticket.dto;

import java.time.Instant;
import java.util.List;

import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        String assignee,
        String category,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments,
        List<TicketStatus> allowedTransitions
) {
}
