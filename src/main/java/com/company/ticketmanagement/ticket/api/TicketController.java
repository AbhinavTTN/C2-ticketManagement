package com.company.ticketmanagement.ticket.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.ticketmanagement.ticket.application.TicketService;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.dto.AddCommentRequest;
import com.company.ticketmanagement.ticket.dto.CreateTicketRequest;
import com.company.ticketmanagement.ticket.dto.TicketPageResponse;
import com.company.ticketmanagement.ticket.dto.TicketResponse;
import com.company.ticketmanagement.ticket.dto.TransitionTicketRequest;
import com.company.ticketmanagement.ticket.dto.UpdateTicketRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.create(request);
    }

    @GetMapping
    public TicketPageResponse list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ticketService.list(status, q, page, size);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return ticketService.getById(id);
    }

    @PatchMapping("/{id}")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(id, request);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse addComment(@PathVariable Long id, @Valid @RequestBody AddCommentRequest request) {
        return ticketService.addComment(id, request);
    }

    @PostMapping("/{id}/status")
    public TicketResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionTicketRequest request) {
        return ticketService.transition(id, request);
    }
}
