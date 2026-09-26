package com.company.ticketmanagement.ask.application;

import java.util.List;

public final class AskPrompt {

    public static final String NO_MATCH = "No relevant tickets were found.";

    private AskPrompt() {
    }

    public static String build(String question, List<RetrievedChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Answer the question using only the ticket excerpts below. ");
        prompt.append("Every factual statement must come from these excerpts. ");
        prompt.append("Name the ticket id for each fact you use. ");
        prompt.append("Do not add causes, resolutions, or tickets that are not in the excerpts.\n\n");
        prompt.append("Question:\n").append(question).append("\n\nExcerpts:\n");
        for (RetrievedChunk chunk : chunks) {
            prompt.append("[ticket ").append(chunk.ticketId()).append("]\n");
            prompt.append(chunk.text()).append("\n\n");
        }
        return prompt.toString();
    }
}
