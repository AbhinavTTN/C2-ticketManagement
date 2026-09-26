package com.company.ticketmanagement.ticket.dto;

import com.company.ticketmanagement.ticket.domain.TicketStatus;

import jakarta.validation.constraints.NotNull;

public record TransitionTicketRequest(
        @NotNull(message = "Status is required.")
        TicketStatus status
) {
}
