package com.company.ticketmanagement.ask.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(String baseUrl, String model, String apiKey) {
}
