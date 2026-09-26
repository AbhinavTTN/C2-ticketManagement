package com.company.ticketmanagement.ingestion.domain;

import java.time.Instant;

import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_status", nullable = false, length = 32)
    private TicketStatus snapshotStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_priority", nullable = false, length = 32)
    private TicketPriority snapshotPriority;

    @Column(name = "snapshot_assignee", length = 120)
    private String snapshotAssignee;

    @Column(name = "snapshot_category", nullable = false, length = 64)
    private String snapshotCategory;

    @Column(name = "canonical_text", nullable = false, columnDefinition = "text")
    private String canonicalText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(Long ticketId) {
        this.ticketId = ticketId;
    }

    public void replaceSnapshot(
            String contentHash,
            TicketStatus status,
            TicketPriority priority,
            String assignee,
            String category,
            String canonicalText,
            Instant updatedAt) {
        this.contentHash = contentHash;
        this.snapshotStatus = status;
        this.snapshotPriority = priority;
        this.snapshotAssignee = assignee;
        this.snapshotCategory = category;
        this.canonicalText = canonicalText;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getContentHash() {
        return contentHash;
    }
}
