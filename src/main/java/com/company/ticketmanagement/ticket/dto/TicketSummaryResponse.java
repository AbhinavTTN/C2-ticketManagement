package com.company.ticketmanagement.ticket.dto;

import java.time.Instant;

import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

public record TicketSummaryResponse(
        Long id,
        String title,
        TicketStatus status,
        TicketPriority priority,
        String assignee,
        String category,
        Instant updatedAt
) {
}
