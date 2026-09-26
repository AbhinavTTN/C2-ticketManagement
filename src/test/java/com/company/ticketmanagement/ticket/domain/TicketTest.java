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

class TicketTest {

    @ParameterizedTest
    @MethodSource("validTransitions")
    void transition_whenValid_updatesStatus(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticketIn(from);

        ticket.transitionTo(to);

        assertThat(ticket.getStatus()).isEqualTo(to);
        assertThat(ticket.getUpdatedAt()).isAfterOrEqualTo(ticket.getCreatedAt());
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void transition_whenInvalid_throwsConflictAndKeepsStatus(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticketIn(from);
        var updatedAt = ticket.getUpdatedAt();

        assertThatThrownBy(() -> ticket.transitionTo(to))
                .isInstanceOf(TicketStateConflictException.class);
        assertThat(ticket.getStatus()).isEqualTo(from);
        assertThat(ticket.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void transitionMatrix_isExhaustive() {
        Set<String> listed = Stream.concat(validTransitions(), invalidTransitions())
                .map(args -> args.get()[0] + "->" + args.get()[1])
                .collect(Collectors.toSet());

        Set<String> allPairs = EnumSet.allOf(TicketStatus.class).stream()
                .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream()
                        .map(to -> from + "->" + to))
                .collect(Collectors.toSet());

        assertThat(listed).containsExactlyInAnyOrderElementsOf(allPairs);
    }

    @Test
    void create_startsOpenWithNoComments() {
        Ticket ticket = new Ticket("Email bounce", "Billing mail fails", TicketPriority.HIGH, null, "billing");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getCategory()).isEqualTo("billing");
        assertThat(ticket.getComments()).isEmpty();
    }

    @Test
    void updateDetails_whenAllNull_doesNotChangeUpdatedAt() {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.LOW, "Alex", "ops");
        var updatedAt = ticket.getUpdatedAt();

        ticket.updateDetails(null, null, null, null, null);

        assertThat(ticket.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void updateDetails_blankAssignee_clearsAssignee() {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.LOW, "Alex", "ops");

        ticket.updateDetails(null, null, null, "  ", null);

        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getCategory()).isEqualTo("ops");
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
        return EnumSet.allOf(TicketStatus.class).stream()
                .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream()
                        .filter(to -> !from.canTransitionTo(to))
                        .map(to -> Arguments.arguments(from, to)));
    }

    private static Ticket ticketIn(TicketStatus status) {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.MEDIUM, "Alex", "ops");
        switch (status) {
            case OPEN -> {
            }
            case IN_PROGRESS -> ticket.transitionTo(TicketStatus.IN_PROGRESS);
            case RESOLVED -> {
                ticket.transitionTo(TicketStatus.IN_PROGRESS);
                ticket.transitionTo(TicketStatus.RESOLVED);
            }
            case CLOSED -> {
                ticket.transitionTo(TicketStatus.IN_PROGRESS);
                ticket.transitionTo(TicketStatus.RESOLVED);
                ticket.transitionTo(TicketStatus.CLOSED);
            }
            case CANCELLED -> ticket.transitionTo(TicketStatus.CANCELLED);
        }
        return ticket;
    }
}
