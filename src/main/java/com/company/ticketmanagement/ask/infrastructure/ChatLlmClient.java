package com.company.ticketmanagement.ask.infrastructure;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.company.ticketmanagement.ask.application.LanguageModelNotConfiguredException;
import com.company.ticketmanagement.ask.application.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ChatLlmClient(LlmProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String prompt) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.model() == null || properties.model().isBlank()) {
            throw new LanguageModelNotConfiguredException();
        }
        String root = properties.baseUrl().replaceAll("/+$", "");
        String body = restClient.post()
                .uri(root + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                        headers.setBearerAuth(properties.apiKey());
                    }
                })
                .body(new ChatRequest(properties.model(), List.of(new ChatMessage("user", prompt))))
                .retrieve()
                .body(String.class);
        return readContent(body);
    }

    private String readContent(String body) {
        try {
            JsonNode content = objectMapper.readTree(body).path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new IllegalStateException("Language model returned an empty answer.");
            }
            return content.asText();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Language model returned an unreadable answer.");
        }
    }

    private record ChatRequest(String model, List<ChatMessage> messages) {
    }

    private record ChatMessage(String role, String content) {
    }
}
