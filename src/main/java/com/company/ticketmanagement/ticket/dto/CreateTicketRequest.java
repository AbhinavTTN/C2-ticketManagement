package com.company.ticketmanagement.ticket.dto;

import com.company.ticketmanagement.ticket.domain.TicketPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title,

        @NotBlank(message = "Description is required.")
        @Size(max = 10_000, message = "Description must be at most 10000 characters.")
        String description,

        @NotNull(message = "Priority is required.")
        TicketPriority priority,

        @Size(max = 120, message = "Assignee must be at most 120 characters.")
        String assignee,

        @NotBlank(message = "Category is required.")
        @Size(max = 64, message = "Category must be at most 64 characters.")
        String category
) {
    public CreateTicketRequest {
        title = Text.trim(title);
        description = Text.trim(description);
        assignee = Text.blankToNull(assignee);
        category = Text.trim(category);
    }
}
