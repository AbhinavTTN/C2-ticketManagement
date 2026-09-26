package com.company.ticketmanagement.ingestion.infrastructure;

public interface EmbeddingClient {

    float[] embed(String text);

    int dimensions();
}
