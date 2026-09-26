package com.company.ticketmanagement.ticket.domain;

import com.company.ticketmanagement.common.exception.AppException;
import com.company.ticketmanagement.common.exception.ErrorCode;

public class TicketNotFoundException extends AppException {

    public TicketNotFoundException(Long id) {
        super(ErrorCode.TICKET_NOT_FOUND, "Ticket %d was not found.".formatted(id));
    }
}
