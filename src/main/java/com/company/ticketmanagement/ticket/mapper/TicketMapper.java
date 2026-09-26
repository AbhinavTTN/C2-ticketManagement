package com.company.ticketmanagement.ticket.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketStateMachine;
import com.company.ticketmanagement.ticket.dto.CommentResponse;
import com.company.ticketmanagement.ticket.dto.TicketResponse;
import com.company.ticketmanagement.ticket.dto.TicketSummaryResponse;

@Component
public class TicketMapper {

    public TicketResponse toResponse(Ticket ticket) {
        List<CommentResponse> comments = ticket.getComments().stream()
                .map(this::toComment)
                .toList();
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getCategory(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                comments,
                List.copyOf(TicketStateMachine.allowedTransitions(ticket.getStatus())));
    }

    public TicketSummaryResponse toSummary(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getCategory(),
                ticket.getUpdatedAt());
    }

    public CommentResponse toComment(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getAuthor(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}
