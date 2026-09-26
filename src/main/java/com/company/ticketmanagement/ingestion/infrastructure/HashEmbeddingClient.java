package com.company.ticketmanagement.ingestion.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local embedder so the pipeline can write pgvector rows before an embedding
 * provider is chosen. It is not a semantic model. Width must match the
 * {@code vector(n)} column in {@code V2__ingestion_pgvector.sql}.
 */
@Component
public class HashEmbeddingClient implements EmbeddingClient {

    private final int dimensions;

    public HashEmbeddingClient(@Value("${app.ingestion.embedding-dimensions:32}") int dimensions) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("embedding dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        String source = text == null ? "" : text;
        for (int i = 0; i < source.length(); i++) {
            int bucket = Math.floorMod(source.charAt(i) * 31 + i, dimensions);
            vector[bucket] += 1f;
        }
        float norm = 0f;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0f) {
            vector[0] = 1f;
            return vector;
        }
        float scale = (float) (1d / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
