package com.company.ticketmanagement.ingestion.application;

import java.util.EnumSet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.ticketmanagement.ingestion.domain.IngestionStatus;
import com.company.ticketmanagement.ingestion.infrastructure.IngestionRecordRepository;

@Component
public class IngestionRetryJob {

    private final IngestionRecordRepository ingestionRecordRepository;
    private final IngestionService ingestionService;

    public IngestionRetryJob(
            IngestionRecordRepository ingestionRecordRepository,
            IngestionService ingestionService) {
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.ingestionService = ingestionService;
    }

    @Scheduled(fixedDelayString = "${app.ingestion.retry-ms:60000}")
    public void retryOutstanding() {
        for (Long ticketId : ingestionRecordRepository.findTicketIdsByStatusIn(
                EnumSet.of(IngestionStatus.PENDING, IngestionStatus.STALE, IngestionStatus.FAILED))) {
            ingestionService.ingest(ticketId);
        }
    }
}
