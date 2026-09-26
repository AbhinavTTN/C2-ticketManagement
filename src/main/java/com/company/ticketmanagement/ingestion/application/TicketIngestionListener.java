package com.company.ticketmanagement.ingestion.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.company.ticketmanagement.ticket.application.TicketChanged;

@Component
public class TicketIngestionListener {

    private final IngestionService ingestionService;

    public TicketIngestionListener(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketChanged(TicketChanged event) {
        ingestionService.ingest(event.ticketId());
    }
}
