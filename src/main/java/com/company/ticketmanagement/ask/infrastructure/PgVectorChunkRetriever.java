package com.company.ticketmanagement.ask.infrastructure;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.company.ticketmanagement.ask.application.ChunkRetriever;
import com.company.ticketmanagement.ask.application.RetrievedChunk;
import com.company.ticketmanagement.ingestion.infrastructure.EmbeddingClient;
import com.company.ticketmanagement.ingestion.infrastructure.VectorLiterals;

@Repository
public class PgVectorChunkRetriever implements ChunkRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

    public PgVectorChunkRetriever(JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public List<RetrievedChunk> search(String question, int topK) {
        float[] embedding = embeddingClient.embed(question);
        if (embedding.length != embeddingClient.dimensions()) {
            throw new IllegalStateException("Embedding width does not match the configured dimension.");
        }
        String literal = VectorLiterals.toLiteral(embedding);
        return jdbcTemplate.query(
                """
                SELECT ticket_id, chunk_text, 1 - (embedding <=> ?::vector) AS similarity
                FROM chunk_records
                ORDER BY embedding <=> ?::vector, ticket_id, ordinal
                LIMIT ?
                """,
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getLong("ticket_id"),
                        rs.getString("chunk_text"),
                        rs.getDouble("similarity")),
                literal,
                literal,
                topK);
    }
}
