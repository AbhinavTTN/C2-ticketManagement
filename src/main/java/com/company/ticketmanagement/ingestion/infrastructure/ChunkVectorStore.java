package com.company.ticketmanagement.ingestion.infrastructure;

import java.util.List;

import com.company.ticketmanagement.ingestion.application.ChunkDraft;

public interface ChunkVectorStore {

    void replace(long ticketId, long documentId, List<ChunkDraft> chunks, List<float[]> embeddings);
}
