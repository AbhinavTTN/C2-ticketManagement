package com.company.ticketmanagement.ticket.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.company.ticketmanagement.common.exception.GlobalExceptionHandler;
import com.company.ticketmanagement.ticket.application.TicketService;
import com.company.ticketmanagement.ticket.domain.TicketNotFoundException;
import com.company.ticketmanagement.ticket.domain.TicketStateConflictException;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.dto.TicketPageResponse;
import com.company.ticketmanagement.ticket.dto.TicketResponse;

@WebMvcTest(TicketController.class)
@Import(GlobalExceptionHandler.class)
class TicketControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TicketService ticketService;

    @Test
    void create_whenValid_returns201() throws Exception {
        given(ticketService.create(any())).willReturn(sample(TicketStatus.OPEN));

        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Email bounce",
                                  "description": "Outbound mail is bouncing",
                                  "priority": "HIGH",
                                  "category": "billing"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.category").value("billing"));
    }

    @Test
    void create_whenTitleBlank_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "  ", "description": "x", "priority": "LOW", "category": "ops" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").value("Title is required."));
    }

    @Test
    void list_returnsPageEnvelope() throws Exception {
        given(ticketService.list(isNull(), isNull(), eq(0), eq(20)))
                .willReturn(new TicketPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void list_whenStatusInvalid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("status", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void get_whenMissing_returns404() throws Exception {
        given(ticketService.getById(9L)).willThrow(new TicketNotFoundException(9L));

        mockMvc.perform(get("/api/v1/tickets/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Ticket 9 was not found."));
    }

    @Test
    void update_returns200() throws Exception {
        given(ticketService.update(eq(1L), any())).willReturn(sample(TicketStatus.OPEN));

        mockMvc.perform(patch("/api/v1/tickets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Renamed" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Email bounce"));
    }

    @Test
    void comment_whenValid_returns201() throws Exception {
        given(ticketService.addComment(eq(1L), any())).willReturn(sample(TicketStatus.OPEN));

        mockMvc.perform(post("/api/v1/tickets/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "author": "Jordan", "body": "SPF fix scheduled" }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void transition_whenInvalid_returns409() throws Exception {
        given(ticketService.transition(eq(1L), any()))
                .willThrow(new TicketStateConflictException(TicketStatus.OPEN, TicketStatus.CLOSED));

        mockMvc.perform(post("/api/v1/tickets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CLOSED" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Cannot transition a ticket from OPEN to CLOSED."));
    }

    private static TicketResponse sample(TicketStatus status) {
        Instant now = Instant.parse("2026-09-24T10:00:00Z");
        return new TicketResponse(
                1L,
                "Email bounce",
                "Outbound mail is bouncing",
                status,
                com.company.ticketmanagement.ticket.domain.TicketPriority.HIGH,
                "Riley",
                "billing",
                now,
                now,
                List.of(),
                List.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
    }
}
