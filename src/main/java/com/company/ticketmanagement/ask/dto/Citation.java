package com.company.ticketmanagement.ask.dto;

import com.company.ticketmanagement.ticket.domain.TicketStatus;

public record Citation(Long ticketId, String title, TicketStatus status) {
}
