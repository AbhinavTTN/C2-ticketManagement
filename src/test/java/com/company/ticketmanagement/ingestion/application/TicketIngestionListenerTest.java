package com.company.ticketmanagement.ingestion.application;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.ticketmanagement.ticket.application.TicketChanged;

@ExtendWith(MockitoExtension.class)
class TicketIngestionListenerTest {

    @Mock
    IngestionService ingestionService;

    @Test
    void onTicketChanged_reingestsThatTicket() {
        TicketIngestionListener listener = new TicketIngestionListener(ingestionService);

        listener.onTicketChanged(new TicketChanged(7L));

        verify(ingestionService).ingest(7L);
    }
}
