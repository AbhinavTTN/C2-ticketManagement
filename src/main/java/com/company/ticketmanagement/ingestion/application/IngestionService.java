package com.company.ticketmanagement.ingestion.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.company.ticketmanagement.ingestion.infrastructure.EmbeddingClient;
import com.company.ticketmanagement.ticket.domain.TicketNotFoundException;

@Service
public class IngestionService {

    private final IngestionTx ingestionTx;
    private final EmbeddingClient embeddingClient;

    public IngestionService(IngestionTx ingestionTx, EmbeddingClient embeddingClient) {
        this.ingestionTx = ingestionTx;
        this.embeddingClient = embeddingClient;
    }

    public void ingest(Long ticketId) {
        PreparedIngestion prepared;
        try {
            prepared = ingestionTx.prepare(ticketId);
        } catch (TicketNotFoundException ex) {
            return;
        } catch (RuntimeException ex) {
            ingestionTx.markFailed(ticketId);
            return;
        }
        if (prepared.skip()) {
            return;
        }
        try {
            List<float[]> embeddings = new ArrayList<>(prepared.chunks().size());
            for (ChunkDraft chunk : prepared.chunks()) {
                float[] embedding = embeddingClient.embed(chunk.text());
                if (embedding.length != embeddingClient.dimensions()) {
                    throw new IllegalStateException("Embedding width does not match the configured dimension.");
                }
                embeddings.add(embedding);
            }
            ingestionTx.store(prepared, embeddings);
        } catch (RuntimeException ex) {
            ingestionTx.markFailed(ticketId);
        }
    }
}
