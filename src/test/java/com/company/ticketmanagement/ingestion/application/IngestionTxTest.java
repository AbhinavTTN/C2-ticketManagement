package com.company.ticketmanagement.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.ticketmanagement.ingestion.domain.IngestionRecord;
import com.company.ticketmanagement.ingestion.domain.IngestionStatus;
import com.company.ticketmanagement.ingestion.domain.KnowledgeDocument;
import com.company.ticketmanagement.ingestion.infrastructure.ChunkVectorStore;
import com.company.ticketmanagement.ingestion.infrastructure.IngestionRecordRepository;
import com.company.ticketmanagement.ingestion.infrastructure.KnowledgeDocumentRepository;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;

@ExtendWith(MockitoExtension.class)
class IngestionTxTest {

    @Mock
    TicketRepository ticketRepository;

    @Mock
    KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    IngestionRecordRepository ingestionRecordRepository;

    @Mock
    ChunkVectorStore chunkVectorStore;

    IngestionTx ingestionTx;

    @BeforeEach
    void setUp() {
        ingestionTx = new IngestionTx(
                ticketRepository,
                knowledgeDocumentRepository,
                ingestionRecordRepository,
                new TicketChunker(),
                chunkVectorStore);
    }

    @Test
    void prepare_whenStatusChanges_marksStaleAndRebuildsChunks() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN);
        String indexedHash = CanonicalDocument.from(ticket).contentHash();
        ticket.transitionTo(TicketStatus.IN_PROGRESS);
        IngestionRecord record = indexed(indexedHash);
        stub(ticket, record);

        PreparedIngestion prepared = ingestionTx.prepare(7L);

        assertThat(prepared.skip()).isFalse();
        assertThat(prepared.contentHash()).isNotEqualTo(indexedHash);
        assertThat(prepared.chunks()).isNotEmpty();
        assertThat(prepared.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.status()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(chunk.ticketId()).isEqualTo(7L);
            assertThat(chunk.priority()).isEqualTo(TicketPriority.HIGH);
            assertThat(chunk.category()).isEqualTo("network");
        });
        assertThat(record.getStatus()).isEqualTo(IngestionStatus.STALE);
        verify(chunkVectorStore, never()).replace(anyLong(), anyLong(), any(), any());
    }

    @Test
    void prepare_whenDetailsChange_marksStale() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN);
        String indexedHash = CanonicalDocument.from(ticket).contentHash();
        ticket.updateDetails("VPN timeout after the gateway change", null, null, null, null);
        IngestionRecord record = indexed(indexedHash);
        stub(ticket, record);

        PreparedIngestion prepared = ingestionTx.prepare(7L);

        assertThat(prepared.skip()).isFalse();
        assertThat(prepared.chunks().getFirst().text()).contains("VPN timeout after the gateway change");
        assertThat(record.getStatus()).isEqualTo(IngestionStatus.STALE);
    }

    @Test
    void prepare_whenNothingChanged_skipsEmbedding() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN);
        IngestionRecord record = indexed(CanonicalDocument.from(ticket).contentHash());
        stub(ticket, record);

        PreparedIngestion prepared = ingestionTx.prepare(7L);

        assertThat(prepared.skip()).isTrue();
        assertThat(record.getStatus()).isEqualTo(IngestionStatus.INDEXED);
    }

    private void stub(Ticket ticket, IngestionRecord record) throws Exception {
        KnowledgeDocument document = new KnowledgeDocument(7L);
        setId(document, 4L);
        given(ticketRepository.findByIdWithComments(7L)).willReturn(Optional.of(ticket));
        given(knowledgeDocumentRepository.findByTicketId(7L)).willReturn(Optional.of(document));
        given(knowledgeDocumentRepository.saveAndFlush(document)).willReturn(document);
        given(ingestionRecordRepository.findByTicketId(7L)).willReturn(Optional.of(record));
    }

    private static Ticket ticket(TicketStatus status) throws Exception {
        Ticket ticket = new Ticket("VPN timeout", "Office VPN drops hourly", TicketPriority.HIGH, "Sam", "network");
        setId(ticket, 7L);
        if (status != TicketStatus.OPEN) {
            ticket.transitionTo(status);
        }
        return ticket;
    }

    private static IngestionRecord indexed(String hash) {
        IngestionRecord record = new IngestionRecord(7L);
        record.markIndexed(hash, Instant.parse("2026-09-26T00:00:00Z"));
        return record;
    }

    private static void setId(Object target, long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
