package com.company.ticketmanagement.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.company.ticketmanagement.common.exception.RequestValidationException;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketNotFoundException;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStateConflictException;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.dto.AddCommentRequest;
import com.company.ticketmanagement.ticket.dto.CreateTicketRequest;
import com.company.ticketmanagement.ticket.dto.TransitionTicketRequest;
import com.company.ticketmanagement.ticket.dto.UpdateTicketRequest;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;
import com.company.ticketmanagement.ticket.mapper.TicketMapper;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    TicketRepository ticketRepository;

    TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, new TicketMapper());
    }

    @Test
    void create_startsOpen() {
        given(ticketRepository.save(any(Ticket.class))).willAnswer(invocation -> invocation.getArgument(0));

        var response = ticketService.create(
                new CreateTicketRequest("  Login fails  ", " Users cannot sign in ", TicketPriority.HIGH, "  ", "billing"));

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.title()).isEqualTo("Login fails");
        assertThat(response.assignee()).isNull();
        assertThat(response.category()).isEqualTo("billing");
        assertThat(response.allowedTransitions()).containsExactly(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED);
    }

    @Test
    void getById_whenMissing_throwsTicketNotFound() {
        given(ticketRepository.findByIdWithComments(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getById(7L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessage("Ticket 7 was not found.");
    }

    @Test
    void update_changesFieldsAndLeavesStatus() {
        Ticket ticket = new Ticket("Old", "Old desc", TicketPriority.LOW, "Pat", "ops");
        given(ticketRepository.findByIdWithComments(1L)).willReturn(Optional.of(ticket));

        var response = ticketService.update(
                1L,
                new UpdateTicketRequest("New title", null, TicketPriority.URGENT, "  ", null));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("Old desc");
        assertThat(response.priority()).isEqualTo(TicketPriority.URGENT);
        assertThat(response.assignee()).isNull();
        assertThat(response.category()).isEqualTo("ops");
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void addComment_appendsComment() {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.LOW, null, "ops");
        given(ticketRepository.findByIdWithComments(1L)).willReturn(Optional.of(ticket));

        var response = ticketService.addComment(1L, new AddCommentRequest(" Jordan ", " SPF fix "));

        assertThat(response.comments()).hasSize(1);
        assertThat(response.comments().getFirst().author()).isEqualTo("Jordan");
        assertThat(response.comments().getFirst().body()).isEqualTo("SPF fix");
    }

    @Test
    void list_whenPageInvalid_throwsBeforeSearch() {
        assertThatThrownBy(() -> ticketService.list(null, null, -1, 20))
                .isInstanceOf(RequestValidationException.class);
        verify(ticketRepository, never()).search(any(), any(), any());
    }

    @Test
    void list_appliesStatusAndOmitsBlankKeyword() {
        given(ticketRepository.search(eq(TicketStatus.OPEN), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0));

        var page = ticketService.list(TicketStatus.OPEN, "   ", 0, 20);

        assertThat(page.totalElements()).isZero();
        verify(ticketRepository).search(eq(TicketStatus.OPEN), isNull(), any(Pageable.class));
    }

    @Test
    void list_escapesLikeMetacharacters() {
        given(ticketRepository.search(isNull(), eq("%100\\%%"), any(Pageable.class)))
                .willReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0));

        var page = ticketService.list(null, "100%", 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalPages()).isZero();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).search(isNull(), eq("%100\\%%"), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").isDescending()).isTrue();
    }

    @Test
    void transition_whenInvalid_throwsConflictAndKeepsStatus() {
        Ticket ticket = new Ticket("Title", "Desc", TicketPriority.LOW, null, "ops");
        given(ticketRepository.findByIdWithComments(1L)).willReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.transition(1L, new TransitionTicketRequest(TicketStatus.CLOSED)))
                .isInstanceOf(TicketStateConflictException.class)
                .hasMessage("Cannot transition a ticket from OPEN to CLOSED.");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        verify(ticketRepository, never()).save(any());
    }
}
