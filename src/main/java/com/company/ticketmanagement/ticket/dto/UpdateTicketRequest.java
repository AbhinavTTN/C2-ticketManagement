package com.company.ticketmanagement.ticket.dto;

import com.company.ticketmanagement.ticket.domain.TicketPriority;

import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(
        @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters.")
        String title,

        @Size(min = 1, max = 10_000, message = "Description must be between 1 and 10000 characters.")
        String description,

        TicketPriority priority,

        @Size(max = 120, message = "Assignee must be at most 120 characters.")
        String assignee,

        @Size(min = 1, max = 64, message = "Category must be between 1 and 64 characters.")
        String category
) {
    public UpdateTicketRequest {
        title = Text.trim(title);
        description = Text.trim(description);
        assignee = Text.trim(assignee);
        category = Text.trim(category);
    }
}
