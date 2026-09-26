package com.company.ticketmanagement.ingestion.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.company.ticketmanagement.ingestion.domain.IngestionRecord;
import com.company.ticketmanagement.ingestion.domain.IngestionStatus;
import com.company.ticketmanagement.ingestion.domain.KnowledgeDocument;
import com.company.ticketmanagement.ingestion.infrastructure.ChunkVectorStore;
import com.company.ticketmanagement.ingestion.infrastructure.IngestionRecordRepository;
import com.company.ticketmanagement.ingestion.infrastructure.KnowledgeDocumentRepository;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketNotFoundException;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;

@Service
public class IngestionTx {

    private final TicketRepository ticketRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final TicketChunker ticketChunker;
    private final ChunkVectorStore chunkVectorStore;

    public IngestionTx(
            TicketRepository ticketRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            IngestionRecordRepository ingestionRecordRepository,
            TicketChunker ticketChunker,
            ChunkVectorStore chunkVectorStore) {
        this.ticketRepository = ticketRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.ticketChunker = ticketChunker;
        this.chunkVectorStore = chunkVectorStore;
    }

    @Transactional
    public PreparedIngestion prepare(Long ticketId) {
        Ticket ticket = ticketRepository.findByIdWithComments(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        CanonicalDocument canonical = CanonicalDocument.from(ticket);
        List<ChunkDraft> chunks = ticketChunker.chunk(ticket);
        KnowledgeDocument document = knowledgeDocumentRepository.findByTicketId(ticketId)
                .orElseGet(() -> new KnowledgeDocument(ticketId));
        document.replaceSnapshot(
                canonical.contentHash(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getCategory(),
                canonical.text(),
                Instant.now());
        document = knowledgeDocumentRepository.saveAndFlush(document);

        IngestionRecord record = ingestionRecordRepository.findByTicketId(ticketId)
                .orElseGet(() -> new IngestionRecord(ticketId));
        if (canonical.contentHash().equals(record.getIndexedContentHash())) {
            record.markIndexed(canonical.contentHash(), Instant.now());
            ingestionRecordRepository.save(record);
            return PreparedIngestion.skipped();
        }
        IngestionStatus next = record.getIndexedContentHash() == null
                ? IngestionStatus.PENDING
                : IngestionStatus.STALE;
        record.markAttempt(next, Instant.now());
        ingestionRecordRepository.save(record);
        return new PreparedIngestion(false, ticketId, document.getId(), canonical.contentHash(), chunks);
    }

    @Transactional
    public void store(PreparedIngestion prepared, List<float[]> embeddings) {
        chunkVectorStore.replace(prepared.ticketId(), prepared.documentId(), prepared.chunks(), embeddings);
        IngestionRecord record = ingestionRecordRepository.findByTicketId(prepared.ticketId())
                .orElseGet(() -> new IngestionRecord(prepared.ticketId()));
        record.markIndexed(prepared.contentHash(), Instant.now());
        ingestionRecordRepository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long ticketId) {
        IngestionRecord record = ingestionRecordRepository.findByTicketId(ticketId)
                .orElseGet(() -> new IngestionRecord(ticketId));
        record.markFailed(Instant.now());
        ingestionRecordRepository.save(record);
    }
}
