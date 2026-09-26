package com.company.ticketmanagement.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.ticketmanagement.ingestion.infrastructure.EmbeddingClient;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    IngestionTx ingestionTx;

    @Mock
    EmbeddingClient embeddingClient;

    @Test
    void ingest_storesOneEmbeddingPerChunkWithMetadata() {
        ChunkDraft chunk = new ChunkDraft(0, "Title: VPN\n\nOffice VPN drops", 7L, TicketStatus.OPEN, TicketPriority.HIGH, null, "network");
        PreparedIngestion prepared = new PreparedIngestion(false, 7L, 4L, "abc", List.of(chunk));
        given(ingestionTx.prepare(7L)).willReturn(prepared);
        given(embeddingClient.dimensions()).willReturn(2);
        given(embeddingClient.embed(chunk.text())).willReturn(new float[] {0.6f, 0.8f});
        IngestionService service = new IngestionService(ingestionTx, embeddingClient);

        service.ingest(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<float[]>> vectors = ArgumentCaptor.forClass(List.class);
        verify(ingestionTx).store(eq(prepared), vectors.capture());
        assertThat(vectors.getValue()).containsExactly(new float[] {0.6f, 0.8f});
        verify(ingestionTx, never()).markFailed(any());
    }

    @Test
    void ingest_whenEmbeddingFails_doesNotStore() {
        ChunkDraft chunk = new ChunkDraft(0, "body", 7L, TicketStatus.OPEN, TicketPriority.LOW, "Alex", "ops");
        given(ingestionTx.prepare(7L)).willReturn(new PreparedIngestion(false, 7L, 1L, "abc", List.of(chunk)));
        given(embeddingClient.embed(any())).willThrow(new IllegalStateException("embedder down"));
        IngestionService service = new IngestionService(ingestionTx, embeddingClient);

        service.ingest(7L);

        verify(ingestionTx).markFailed(7L);
        verify(ingestionTx, never()).store(any(), any());
    }

    @Test
    void ingest_whenHashUnchanged_skipsEmbedding() {
        given(ingestionTx.prepare(7L)).willReturn(PreparedIngestion.skipped());
        IngestionService service = new IngestionService(ingestionTx, embeddingClient);

        service.ingest(7L);

        verify(embeddingClient, never()).embed(any());
        verify(ingestionTx, never()).store(any(), any());
    }
}
