package com.company.ticketmanagement.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.company.ticketmanagement.ingestion.domain.IngestionStatus;
import com.company.ticketmanagement.ingestion.infrastructure.IngestionRecordRepository;
import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;

@SpringBootTest
@Testcontainers
class IngestionPipelineIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    IngestionService ingestionService;

    @Autowired
    IngestionRecordRepository ingestionRecordRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE chunk_records, knowledge_documents, ingestion_records, comments, tickets
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void ingest_whenTicketMissing_doesNotWriteRows() {
        ingestionService.ingest(999_999L);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_records", Integer.class)).isZero();
        assertThat(ingestionRecordRepository.findByTicketId(999_999L)).isEmpty();
    }

    @Test
    void ingest_storesOneChunkPerParagraphAndRefreshesStatus() {
        Ticket ticket = new Ticket(
                "Email bounce",
                "First paragraph.\n\nSecond paragraph.",
                TicketPriority.HIGH,
                "Riley",
                "billing");
        ticket.addComment(new Comment("Sam", "Checked logs", Instant.parse("2026-09-24T09:00:00Z")));
        ticket.addComment(new Comment(
                "Jordan",
                "SPF record added\n\nReset the queue",
                Instant.parse("2026-09-24T10:00:00Z")));
        Long id = ticketRepository.saveAndFlush(ticket).getId();

        ingestionService.ingest(id);

        List<Map<String, Object>> chunks = chunks();
        assertThat(chunks).hasSize(5);
        assertThat(chunks).allSatisfy(row -> {
            assertThat(row.get("ticket_id")).isEqualTo(id);
            assertThat(row.get("ticket_id_meta")).isEqualTo(id);
            assertThat(row.get("status")).isEqualTo("OPEN");
            assertThat(row.get("priority")).isEqualTo("HIGH");
            assertThat(row.get("assignee")).isEqualTo("Riley");
            assertThat(row.get("category")).isEqualTo("billing");
            assertThat(((Number) row.get("dims")).intValue()).isEqualTo(32);
        });
        assertThat(text(chunks, 0)).contains("Section: description").contains("First paragraph.").doesNotContain("Second paragraph");
        assertThat(text(chunks, 1)).contains("Second paragraph.").doesNotContain("Checked logs");
        assertThat(text(chunks, 2)).contains("Section: comment").contains("Author: Sam").contains("Checked logs");
        assertThat(text(chunks, 3)).contains("Author: Jordan").contains("SPF record added").doesNotContain("Reset the queue");
        assertThat(text(chunks, 4)).contains("Reset the queue").doesNotContain("SPF record added");
        assertThat(ingestionRecordRepository.findByTicketId(id).orElseThrow().getStatus())
                .isEqualTo(IngestionStatus.INDEXED);

        ingestionService.ingest(id);

        assertThat(chunks()).hasSize(5);

        Ticket stored = ticketRepository.findById(id).orElseThrow();
        stored.transitionTo(TicketStatus.IN_PROGRESS);
        ticketRepository.saveAndFlush(stored);
        ingestionService.ingest(id);

        assertThat(chunks()).hasSize(5);
        assertThat(chunks()).allSatisfy(row -> assertThat(row.get("status")).isEqualTo("IN_PROGRESS"));
    }

    private List<Map<String, Object>> chunks() {
        return jdbcTemplate.queryForList("""
                SELECT ticket_id, ticket_id_meta, ordinal, status, priority, assignee, category,
                       chunk_text, vector_dims(embedding) AS dims
                FROM chunk_records
                ORDER BY ordinal
                """);
    }

    private static String text(List<Map<String, Object>> chunks, int ordinal) {
        return (String) chunks.get(ordinal).get("chunk_text");
    }
}
