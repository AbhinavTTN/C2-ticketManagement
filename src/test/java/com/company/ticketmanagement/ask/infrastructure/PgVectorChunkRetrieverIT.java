package com.company.ticketmanagement.ask.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.company.ticketmanagement.ingestion.infrastructure.HashEmbeddingClient;
import com.company.ticketmanagement.ingestion.infrastructure.VectorLiterals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, PgVectorChunkRetriever.class, HashEmbeddingClient.class})
@Testcontainers
class PgVectorChunkRetrieverIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    HashEmbeddingClient embeddingClient;

    @Autowired
    PgVectorChunkRetriever retriever;

    @Test
    void search_returnsNearestChunksFirst() {
        long vpnId = insertTicket("VPN timeout", "Office VPN drops hourly");
        long printerId = insertTicket("Printer jam", "Floor 3 printer jammed");
        insertChunk(vpnId, "Office VPN drops hourly", embeddingClient.embed("Office VPN drops hourly"));
        insertChunk(printerId, "Floor 3 printer jammed", embeddingClient.embed("Floor 3 printer jammed"));

        var hits = retriever.search("Office VPN drops hourly", 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).ticketId()).isEqualTo(vpnId);
        assertThat(hits.get(0).text()).isEqualTo("Office VPN drops hourly");
        assertThat(hits.get(0).similarity()).isCloseTo(1.0, within(0.001));
        assertThat(hits.get(1).ticketId()).isEqualTo(printerId);
        assertThat(hits.get(0).similarity()).isGreaterThan(hits.get(1).similarity());
    }

    private long insertTicket(String title, String description) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO tickets (title, description, status, priority, assignee, category, created_at, updated_at)
                VALUES (?, ?, 'OPEN', 'HIGH', 'Sam', 'network', now(), now())
                RETURNING id
                """,
                Long.class,
                title,
                description);
    }

    private void insertChunk(long ticketId, String text, float[] embedding) {
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO knowledge_documents (
                    ticket_id, content_hash, snapshot_status, snapshot_priority, snapshot_assignee,
                    snapshot_category, canonical_text, updated_at
                ) VALUES (?, 'hash', 'OPEN', 'HIGH', 'Sam', 'network', ?, now())
                RETURNING id
                """,
                Long.class,
                ticketId,
                text);
        jdbcTemplate.update(
                """
                INSERT INTO chunk_records (
                    id, ticket_id, document_id, ordinal, chunk_text, ticket_id_meta,
                    status, priority, assignee, category, embedding
                ) VALUES (?::uuid, ?, ?, 0, ?, ?, 'OPEN', 'HIGH', 'Sam', 'network', ?::vector)
                """,
                UUID.randomUUID().toString(),
                ticketId,
                documentId,
                text,
                ticketId,
                VectorLiterals.toLiteral(embedding));
    }
}
