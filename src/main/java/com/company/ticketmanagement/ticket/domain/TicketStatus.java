package com.company.ticketmanagement.ticket.domain;

import java.util.Set;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public Set<TicketStatus> allowedTransitions() {
        return TicketStateMachine.allowedTransitions(this);
    }

    public boolean canTransitionTo(TicketStatus target) {
        return TicketStateMachine.canTransition(this, target);
    }
}
