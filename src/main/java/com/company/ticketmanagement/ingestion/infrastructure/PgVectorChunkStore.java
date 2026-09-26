package com.company.ticketmanagement.ingestion.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.company.ticketmanagement.ingestion.application.ChunkDraft;

@Repository
public class PgVectorChunkStore implements ChunkVectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

    public PgVectorChunkStore(JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void replace(long ticketId, long documentId, List<ChunkDraft> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Each chunk needs one embedding.");
        }
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDraft chunk = chunks.get(i);
            float[] embedding = embeddings.get(i);
            if (chunk.ticketId() != ticketId) {
                throw new IllegalArgumentException("Chunk ticketId does not match the ticket being indexed.");
            }
            if (embedding == null || embedding.length != embeddingClient.dimensions()) {
                throw new IllegalArgumentException("Embedding width does not match the pgvector column.");
            }
        }

        jdbcTemplate.update("DELETE FROM chunk_records WHERE ticket_id = ?", ticketId);
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDraft chunk = chunks.get(i);
            jdbcTemplate.update(
                    """
                    INSERT INTO chunk_records (
                        id, ticket_id, document_id, ordinal, chunk_text, ticket_id_meta,
                        status, priority, assignee, category, embedding
                    ) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                    """,
                    UUID.randomUUID().toString(),
                    ticketId,
                    documentId,
                    chunk.ordinal(),
                    chunk.text(),
                    chunk.ticketId(),
                    chunk.status().name(),
                    chunk.priority().name(),
                    chunk.assignee(),
                    chunk.category(),
                    VectorLiterals.toLiteral(embeddings.get(i)));
        }
    }
}
