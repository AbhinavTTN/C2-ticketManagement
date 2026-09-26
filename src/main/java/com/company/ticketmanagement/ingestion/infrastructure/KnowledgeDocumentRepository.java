package com.company.ticketmanagement.ingestion.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.ticketmanagement.ingestion.domain.KnowledgeDocument;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Optional<KnowledgeDocument> findByTicketId(Long ticketId);
}
