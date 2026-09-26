package com.company.ticketmanagement.ticket.domain;

import com.company.ticketmanagement.common.exception.AppException;
import com.company.ticketmanagement.common.exception.ErrorCode;

public class TicketStateConflictException extends AppException {

    public TicketStateConflictException(TicketStatus from, TicketStatus to) {
        super(
                ErrorCode.INVALID_STATUS_TRANSITION,
                "Cannot transition a ticket from %s to %s.".formatted(from, to));
    }
}
