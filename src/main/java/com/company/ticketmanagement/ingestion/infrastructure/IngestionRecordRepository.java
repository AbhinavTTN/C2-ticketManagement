package com.company.ticketmanagement.ingestion.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.ticketmanagement.ingestion.domain.IngestionRecord;
import com.company.ticketmanagement.ingestion.domain.IngestionStatus;

public interface IngestionRecordRepository extends JpaRepository<IngestionRecord, Long> {

    Optional<IngestionRecord> findByTicketId(Long ticketId);

    @Query("SELECT r.ticketId FROM IngestionRecord r WHERE r.status IN :statuses")
    List<Long> findTicketIdsByStatusIn(@Param("statuses") Collection<IngestionStatus> statuses);
}
