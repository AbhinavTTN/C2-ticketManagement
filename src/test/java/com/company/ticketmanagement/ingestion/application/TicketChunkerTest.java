package com.company.ticketmanagement.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.company.ticketmanagement.ticket.domain.Comment;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

class TicketChunkerTest {

    private final TicketChunker chunker = new TicketChunker();

    @Test
    void chunk_splitsDescriptionAndKeepsEachCommentSeparate() throws Exception {
        Ticket ticket = new Ticket(
                "Email bounce",
                "First paragraph.\n\nSecond paragraph.",
                TicketPriority.HIGH,
                "Riley",
                "billing");
        setId(ticket, 7L);
        ticket.addComment(new Comment("Jordan", "SPF record added", Instant.parse("2026-09-24T10:00:00Z")));
        ticket.addComment(new Comment("Sam", "Checked logs\n\nReset the queue", Instant.parse("2026-09-24T09:00:00Z")));

        var chunks = chunker.chunk(ticket);

        assertThat(chunks).hasSize(5);
        assertThat(chunks).extracting(ChunkDraft::ordinal).containsExactly(0, 1, 2, 3, 4);
        assertThat(chunks.get(0).text()).contains("Section: description").contains("First paragraph.");
        assertThat(chunks.get(0).text()).doesNotContain("Second paragraph");
        assertThat(chunks.get(2).text()).contains("Section: comment").contains("Author: Sam").contains("Checked logs");
        assertThat(chunks.get(2).text()).doesNotContain("Reset the queue").doesNotContain("SPF record added");
        assertThat(chunks.get(3).text()).contains("Author: Sam").contains("Reset the queue");
        assertThat(chunks.get(4).text()).contains("Author: Jordan").contains("SPF record added");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.ticketId()).isEqualTo(7L);
            assertThat(chunk.status()).isEqualTo(TicketStatus.OPEN);
            assertThat(chunk.priority()).isEqualTo(TicketPriority.HIGH);
            assertThat(chunk.assignee()).isEqualTo("Riley");
            assertThat(chunk.category()).isEqualTo("billing");
            assertThat(chunk.text()).startsWith("Title: Email bounce");
        });
    }

    @Test
    void chunk_descriptionWithoutBlankLines_isOneChunk() throws Exception {
        Ticket ticket = new Ticket("VPN", "Office VPN drops hourly", TicketPriority.MEDIUM, null, "network");
        setId(ticket, 3L);

        var chunks = chunker.chunk(ticket);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().assignee()).isNull();
        assertThat(chunks.getFirst().text()).contains("Office VPN drops hourly");
    }

    private static void setId(Ticket ticket, long id) throws Exception {
        Field field = Ticket.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(ticket, id);
    }
}
