package com.company.ticketmanagement.ticket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayAutoConfiguration.class)
@Testcontainers
class TicketRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    CommentRepository commentRepository;

    @Test
    void findByIdWithComments_returnsCommentsOldestFirst() {
        Ticket ticket = new Ticket(
                "Email bounce",
                "Outbound mail is bouncing",
                TicketPriority.HIGH,
                "Riley",
                "billing");
        ticket.addComment(new Comment("Jordan", "Later note", Instant.parse("2026-09-24T10:00:00Z")));
        ticket.addComment(new Comment("Riley", "First note", Instant.parse("2026-09-24T09:00:00Z")));
        Long id = ticketRepository.saveAndFlush(ticket).getId();
        entityManager.clear();

        Ticket loaded = ticketRepository.findByIdWithComments(id).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(loaded.getCategory()).isEqualTo("billing");
        assertThat(loaded.getAssignee()).isEqualTo("Riley");
        assertThat(loaded.getComments()).extracting(Comment::getBody).containsExactly("First note", "Later note");
        assertThat(commentRepository.findByTicket_IdOrderByCreatedAtAscIdAsc(id))
                .extracting(Comment::getAuthor)
                .containsExactly("Riley", "Jordan");
    }

    @Test
    void findByIdWithComments_whenMissing_isEmpty() {
        assertThat(ticketRepository.findByIdWithComments(999_999L)).isEmpty();
    }

    @Test
    void save_persistsNullAssignee() {
        Ticket ticket = ticketRepository.saveAndFlush(
                new Ticket("Printer jam", "Floor 3", TicketPriority.LOW, null, "facilities"));

        Ticket loaded = ticketRepository.findById(ticket.getId()).orElseThrow();

        assertThat(loaded.getAssignee()).isNull();
        assertThat(loaded.getComments()).isEmpty();
    }
}
