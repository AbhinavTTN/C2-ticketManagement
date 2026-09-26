package com.company.ticketmanagement.ask.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.company.ticketmanagement.ask.application.AskService;
import com.company.ticketmanagement.ask.application.LanguageModelNotConfiguredException;
import com.company.ticketmanagement.ask.dto.AskResponse;
import com.company.ticketmanagement.ask.dto.Citation;
import com.company.ticketmanagement.common.exception.GlobalExceptionHandler;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

@WebMvcTest(AskController.class)
@Import(GlobalExceptionHandler.class)
class AskControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AskService askService;

    @Test
    void ask_returnsAnswerAndCitations() throws Exception {
        given(askService.ask(any())).willReturn(new AskResponse(
                "What do we know about password reset?",
                true,
                "Reset email never arrives.",
                List.of(new Citation(101L, "Cannot reset password", TicketStatus.OPEN))));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "What do we know about password reset?" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value("Reset email never arrives."))
                .andExpect(jsonPath("$.citations[0].ticketId").value(101))
                .andExpect(jsonPath("$.citations[0].title").value("Cannot reset password"))
                .andExpect(jsonPath("$.citations[0].status").value("OPEN"));
    }

    @Test
    void ask_whenNothingIsRelevant_returnsNoMatch() throws Exception {
        given(askService.ask(any())).willReturn(new AskResponse(
                "What is the status of the lunar rover?",
                false,
                "No relevant tickets were found.",
                List.of()));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "What is the status of the lunar rover?" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.answer").value("No relevant tickets were found."))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void ask_whenQuestionBlank_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "  " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.question").value("Question is required."));
        verify(askService, never()).ask(any());
    }

    @Test
    void ask_whenModelIsNotConfigured_returns503() throws Exception {
        given(askService.ask(any())).willThrow(new LanguageModelNotConfiguredException());

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "What fixed the billing email bounce?" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LANGUAGE_MODEL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.detail").value("Language model is not configured."));
    }
}
