package com.company.ticketmanagement.ticket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

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
    void search_matchesTitleDescriptionAndCommentsIgnoringCase() {
        Ticket billing = saved("Email bounce", "Outbound mail");
        billing.addComment(new Comment("Jordan", "Checked SMTP logs", Instant.parse("2026-09-24T09:00:00Z")));
        ticketRepository.saveAndFlush(billing);
        Ticket printer = saved("Printer jam", "Floor 3");
        entityManager.clear();

        assertThat(ids(null, "%email%")).containsExactly(billing.getId());
        assertThat(ids(null, "%OUTBOUND%")).containsExactly(billing.getId());
        assertThat(ids(null, "%smtp%")).containsExactly(billing.getId());
        assertThat(ids(null, "%jordan%")).containsExactly(billing.getId());
        assertThat(ids(null, "%lunar%")).isEmpty();
        assertThat(ids(null, null)).containsExactlyInAnyOrder(billing.getId(), printer.getId());
    }

    @Test
    void search_appliesStatusAndKeywordTogether() {
        Ticket open = saved("VPN timeout", "Office VPN drops hourly");
        Ticket closed = saved("VPN docs", "How to install the client");
        closed.transitionTo(TicketStatus.IN_PROGRESS);
        closed.transitionTo(TicketStatus.RESOLVED);
        closed.transitionTo(TicketStatus.CLOSED);
        ticketRepository.saveAndFlush(closed);
        entityManager.clear();

        assertThat(ids(TicketStatus.OPEN, "%vpn%")).containsExactly(open.getId());
        assertThat(ids(TicketStatus.CLOSED, "%vpn%")).containsExactly(closed.getId());
        assertThat(ids(TicketStatus.CANCELLED, "%vpn%")).isEmpty();
    }

    @Test
    void search_treatsPercentAsLiteral() {
        Ticket literal = saved("100% done", "capacity");
        saved("100X done", "capacity");
        entityManager.clear();

        assertThat(ids(null, "%100\\% done%")).containsExactly(literal.getId());
    }

    private Ticket saved(String title, String description) {
        return ticketRepository.saveAndFlush(
                new Ticket(title, description, TicketPriority.MEDIUM, null, "ops"));
    }

    private List<Long> ids(TicketStatus status, String keyword) {
        return ticketRepository.search(status, keyword, PageRequest.of(0, 20))
                .map(Ticket::getId)
                .getContent();
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
