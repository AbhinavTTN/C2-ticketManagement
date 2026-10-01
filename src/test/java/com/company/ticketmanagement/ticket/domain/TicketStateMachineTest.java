package com.company.ticketmanagement.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TicketStateMachineTest {

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void transition_whenInvalid_throwsConflictAndKeepsStatus(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticketIn(from);
        var updatedAt = ticket.getUpdatedAt();

        assertThatThrownBy(() -> TicketStateMachine.transition(ticket, to))
                .isInstanceOf(TicketStateConflictException.class)
                .hasMessage("Cannot transition a ticket from %s to %s.".formatted(from, to));
        assertThat(ticket.getStatus()).isEqualTo(from);
        assertThat(ticket.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(TicketStateMachine.canTransition(from, to)).isFalse();
    }

    @Test
    void transition_whenTargetMissing_throwsConflictAndKeepsStatus() {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.LOW, null, "ops");
        var updatedAt = ticket.getUpdatedAt();

        assertThatThrownBy(() -> TicketStateMachine.transition(ticket, null))
                .isInstanceOf(TicketStateConflictException.class)
                .hasMessage("Cannot transition a ticket from OPEN to null.");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(TicketStateMachine.canTransition(TicketStatus.OPEN, null)).isFalse();
    }

    @Test
    void allowedTransitions_forTerminalStatus_isEmptyAndUnmodifiable() {
        assertThat(TicketStateMachine.allowedTransitions(TicketStatus.CLOSED)).isEmpty();
        assertThat(TicketStateMachine.allowedTransitions(TicketStatus.CANCELLED)).isEmpty();
        assertThatThrownBy(() -> TicketStateMachine.allowedTransitions(TicketStatus.OPEN).add(TicketStatus.CLOSED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    void transition_whenValid_updatesStatus(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticketIn(from);

        TicketStateMachine.transition(ticket, to);

        assertThat(ticket.getStatus()).isEqualTo(to);
        assertThat(TicketStateMachine.canTransition(from, to)).isTrue();
        assertThat(TicketStateMachine.allowedTransitions(from)).contains(to);
    }

    @Test
    void transitionMatrix_isExhaustive() {
        Set<String> listed = Stream.concat(validTransitions(), invalidTransitions())
                .map(args -> args.get()[0] + "->" + args.get()[1])
                .collect(Collectors.toSet());
        Set<String> allPairs = EnumSet.allOf(TicketStatus.class).stream()
                .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream().map(to -> from + "->" + to))
                .collect(Collectors.toSet());

        assertThat(listed).containsExactlyInAnyOrderElementsOf(allPairs);
        assertThat(validTransitions().count()).isEqualTo(5);
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.arguments(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
                Arguments.arguments(TicketStatus.OPEN, TicketStatus.CANCELLED),
                Arguments.arguments(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
                Arguments.arguments(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
                Arguments.arguments(TicketStatus.RESOLVED, TicketStatus.CLOSED));
    }

    static Stream<Arguments> invalidTransitions() {
        Set<String> valid = validTransitions()
                .map(args -> args.get()[0] + "->" + args.get()[1])
                .collect(Collectors.toSet());
        return EnumSet.allOf(TicketStatus.class).stream()
                .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream()
                        .filter(to -> !valid.contains(from + "->" + to))
                        .map(to -> Arguments.arguments(from, to)));
    }

    private static Ticket ticketIn(TicketStatus status) {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.MEDIUM, "Alex", "ops");
        switch (status) {
            case OPEN -> {
            }
            case IN_PROGRESS -> TicketStateMachine.transition(ticket, TicketStatus.IN_PROGRESS);
            case RESOLVED -> {
                TicketStateMachine.transition(ticket, TicketStatus.IN_PROGRESS);
                TicketStateMachine.transition(ticket, TicketStatus.RESOLVED);
            }
            case CLOSED -> {
                TicketStateMachine.transition(ticket, TicketStatus.IN_PROGRESS);
                TicketStateMachine.transition(ticket, TicketStatus.RESOLVED);
                TicketStateMachine.transition(ticket, TicketStatus.CLOSED);
            }
            case CANCELLED -> TicketStateMachine.transition(ticket, TicketStatus.CANCELLED);
        }
        return ticket;
    }
}
