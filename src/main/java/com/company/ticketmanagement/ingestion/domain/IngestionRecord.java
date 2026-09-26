package com.company.ticketmanagement.ingestion.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingestion_records")
public class IngestionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IngestionStatus status;

    @Column(name = "indexed_content_hash", length = 64)
    private String indexedContentHash;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    protected IngestionRecord() {
    }

    public IngestionRecord(Long ticketId) {
        this.ticketId = ticketId;
        this.status = IngestionStatus.PENDING;
    }

    public void markAttempt(IngestionStatus status, Instant at) {
        this.status = status;
        this.lastAttemptAt = at;
    }

    public void markIndexed(String contentHash, Instant at) {
        this.status = IngestionStatus.INDEXED;
        this.indexedContentHash = contentHash;
        this.lastSuccessAt = at;
        this.failureReason = null;
    }

    public void markFailed(Instant at) {
        this.status = IngestionStatus.FAILED;
        this.lastAttemptAt = at;
        this.failureReason = "Ingestion failed";
    }

    public Long getTicketId() {
        return ticketId;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public String getIndexedContentHash() {
        return indexedContentHash;
    }
}
