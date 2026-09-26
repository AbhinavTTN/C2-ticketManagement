package com.company.ticketmanagement.ticket.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explicit ticket status table from spec/state-machine.md.
 * Any pair not listed is rejected.
 */
public final class TicketStateMachine {

    private record Edge(TicketStatus from, TicketStatus to) {
    }

    private static final List<Edge> TRANSITIONS = List.of(
            new Edge(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
            new Edge(TicketStatus.OPEN, TicketStatus.CANCELLED),
            new Edge(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
            new Edge(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
            new Edge(TicketStatus.RESOLVED, TicketStatus.CLOSED));

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = index(TRANSITIONS);

    private TicketStateMachine() {
    }

    public static Set<TicketStatus> allowedTransitions(TicketStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    public static boolean canTransition(TicketStatus from, TicketStatus to) {
        return to != null && allowedTransitions(from).contains(to);
    }

    public static void transition(Ticket ticket, TicketStatus target) {
        TicketStatus from = ticket.getStatus();
        if (!canTransition(from, target)) {
            throw new TicketStateConflictException(from, target);
        }
        ticket.applyStatus(target);
    }

    private static Set<TicketStatus> freeze(Set<TicketStatus> targets) {
        if (targets.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(targets));
    }

    private static Map<TicketStatus, Set<TicketStatus>> index(List<Edge> edges) {
        Map<TicketStatus, Set<TicketStatus>> table = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            table.put(status, EnumSet.noneOf(TicketStatus.class));
        }
        for (Edge edge : edges) {
            table.get(edge.from()).add(edge.to());
        }
        table.replaceAll((status, targets) -> freeze(targets));
        return Map.copyOf(table);
    }
}
