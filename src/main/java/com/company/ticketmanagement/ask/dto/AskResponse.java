package com.company.ticketmanagement.ask.dto;

import java.util.List;

public record AskResponse(String question, boolean found, String answer, List<Citation> citations) {

    public AskResponse {
        citations = List.copyOf(citations);
    }
}
