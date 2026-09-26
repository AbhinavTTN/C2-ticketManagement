package com.company.ticketmanagement.ask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "Question is required.")
        @Size(max = 1000, message = "Question must be at most 1000 characters.")
        String question
) {
    public AskRequest {
        question = question == null ? null : question.trim();
    }
}
