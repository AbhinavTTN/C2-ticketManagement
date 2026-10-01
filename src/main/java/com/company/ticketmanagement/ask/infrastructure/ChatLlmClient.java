package com.company.ticketmanagement.ask.infrastructure;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.company.ticketmanagement.ask.application.LanguageModelEmptyAnswerException;
import com.company.ticketmanagement.ask.application.LanguageModelNotConfiguredException;
import com.company.ticketmanagement.ask.application.LlmClient;
import com.fasterxml.jackson.annotation.JsonInclude;
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
        if (Boolean.TRUE.equals(properties.ollamaNative())) {
            return completeWithOllama(prompt);
        }
        return completeWithOpenAi(prompt);
    }

    private String completeWithOllama(String prompt) {
        String body = restClient.post()
                .uri(ollamaChatUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OllamaChatRequest(
                        properties.model(),
                        List.of(new ChatMessage("user", prompt)),
                        properties.think(),
                        false))
                .retrieve()
                .body(String.class);
        return textAt(body, node -> node.path("message").path("content"));
    }

    private String completeWithOpenAi(String prompt) {
        String root = properties.baseUrl().replaceAll("/+$", "");
        String body = restClient.post()
                .uri(root + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                        headers.setBearerAuth(properties.apiKey());
                    }
                })
                .body(new OpenAiChatRequest(
                        properties.model(),
                        List.of(new ChatMessage("user", prompt)),
                        properties.think()))
                .retrieve()
                .body(String.class);
        return textAt(body, node -> node.path("choices").path(0).path("message").path("content"));
    }

    private String ollamaChatUrl() {
        URI base = URI.create(properties.baseUrl().trim());
        if (base.getScheme() == null || base.getAuthority() == null) {
            throw new LanguageModelNotConfiguredException();
        }
        return base.getScheme() + "://" + base.getAuthority() + "/api/chat";
    }

    private String textAt(String body, Function<JsonNode, JsonNode> content) {
        try {
            JsonNode text = content.apply(objectMapper.readTree(body));
            if (!text.isTextual() || text.asText().isBlank()) {
                throw new LanguageModelEmptyAnswerException("The language model returned an empty answer.");
            }
            return text.asText();
        } catch (LanguageModelEmptyAnswerException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LanguageModelEmptyAnswerException("The language model returned an unreadable answer.");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record OpenAiChatRequest(String model, List<ChatMessage> messages, Boolean think) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record OllamaChatRequest(String model, List<ChatMessage> messages, Boolean think, boolean stream) {
    }

    private record ChatMessage(String role, String content) {
    }
}
