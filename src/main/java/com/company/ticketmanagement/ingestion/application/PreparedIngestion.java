package com.company.ticketmanagement.ingestion.application;

import java.util.List;

public record PreparedIngestion(
        boolean skip,
        Long ticketId,
        Long documentId,
        String contentHash,
        List<ChunkDraft> chunks
) {
    public static PreparedIngestion skipped() {
        return new PreparedIngestion(true, null, null, null, List.of());
    }
}
