package com.company.ticketmanagement.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotBlank(message = "Author is required.")
        @Size(max = 120, message = "Author must be at most 120 characters.")
        String author,

        @NotBlank(message = "Body is required.")
        @Size(max = 10_000, message = "Body must be at most 10000 characters.")
        String body
) {
    public AddCommentRequest {
        author = Text.trim(author);
        body = Text.trim(body);
    }
}
