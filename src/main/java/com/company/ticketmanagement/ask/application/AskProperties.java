package com.company.ticketmanagement.ask.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ask")
public record AskProperties(int topK, double similarityThreshold) {

    public AskProperties {
        if (topK < 1) {
            throw new IllegalArgumentException("top-k must be at least 1.");
        }
        if (Double.isNaN(similarityThreshold) || similarityThreshold < -1d || similarityThreshold > 1d) {
            throw new IllegalArgumentException("similarity threshold must be between -1 and 1.");
        }
    }
}
