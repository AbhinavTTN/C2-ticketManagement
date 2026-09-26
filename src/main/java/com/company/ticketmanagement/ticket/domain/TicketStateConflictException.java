package com.company.ticketmanagement.ticket.domain;

public class TicketStateConflictException extends RuntimeException {

    public TicketStateConflictException(TicketStatus from, TicketStatus to) {
        super("Cannot transition a ticket from %s to %s.".formatted(from, to));
    }
}
