package com.company.ticketmanagement.ticket.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PublishingTicketChangeNotifier implements TicketChangeNotifier {

    private final ApplicationEventPublisher publisher;

    public PublishingTicketChangeNotifier(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void changed(Long ticketId) {
        publisher.publishEvent(new TicketChanged(ticketId));
    }
}
