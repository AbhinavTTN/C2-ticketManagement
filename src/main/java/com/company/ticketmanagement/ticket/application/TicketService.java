package com.company.ticketmanagement.ticket.application;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.ticketmanagement.common.exception.RequestValidationException;
import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketNotFoundException;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.dto.AddCommentRequest;
import com.company.ticketmanagement.ticket.dto.CreateTicketRequest;
import com.company.ticketmanagement.ticket.dto.TicketPageResponse;
import com.company.ticketmanagement.ticket.dto.TicketResponse;
import com.company.ticketmanagement.ticket.dto.TransitionTicketRequest;
import com.company.ticketmanagement.ticket.dto.UpdateTicketRequest;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;
import com.company.ticketmanagement.ticket.mapper.TicketMapper;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;

    public TicketService(TicketRepository ticketRepository, TicketMapper ticketMapper) {
        this.ticketRepository = ticketRepository;
        this.ticketMapper = ticketMapper;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        Ticket ticket = new Ticket(
                request.title(),
                request.description(),
                request.priority(),
                request.assignee(),
                request.category());
        return ticketMapper.toResponse(ticketRepository.save(ticket));
    }

    public TicketPageResponse list(TicketStatus status, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new RequestValidationException(
                    "page must be zero or greater and size must be between 1 and 100.");
        }
        Page<Ticket> result = ticketRepository.search(
                status,
                likePattern(keyword),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return new TicketPageResponse(
                result.getContent().stream().map(ticketMapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public TicketResponse getById(Long id) {
        return ticketMapper.toResponse(requireTicket(id));
    }

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request) {
        Ticket ticket = requireTicket(id);
        ticket.updateDetails(
                request.title(),
                request.description(),
                request.priority(),
                request.assignee(),
                request.category());
        return ticketMapper.toResponse(ticket);
    }

    @Transactional
    public TicketResponse addComment(Long id, AddCommentRequest request) {
        Ticket ticket = requireTicket(id);
        ticket.addComment(new Comment(request.author(), request.body()));
        return ticketMapper.toResponse(ticket);
    }

    @Transactional
    public TicketResponse transition(Long id, TransitionTicketRequest request) {
        Ticket ticket = requireTicket(id);
        ticket.transitionTo(request.status());
        return ticketMapper.toResponse(ticket);
    }

    private Ticket requireTicket(Long id) {
        return ticketRepository.findByIdWithComments(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
