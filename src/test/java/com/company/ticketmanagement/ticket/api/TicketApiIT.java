package com.company.ticketmanagement.ticket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.company.ticketmanagement.ingestion.application.TicketIngestionListener;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TicketIngestionListener ticketIngestionListener;

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
    void create_whenTitleBlank_returnsFieldErrorAndDoesNotPersist() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","description":"Body","priority":"LOW","category":"ops"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fields.title").value("Title is required."));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isZero();
    }

    @Test
    void get_whenMissing_returnsNotFoundDetail() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Ticket 999999 was not found."));
    }

    @Test
    void transition_whenInvalid_returnsConflictAndKeepsStatus() throws Exception {
        int id = createTicket();

        mockMvc.perform(post("/api/v1/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLOSED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Cannot transition a ticket from OPEN to CLOSED."));

        mockMvc.perform(get("/api/v1/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void crud_createsListsUpdatesCommentsAndTransitions() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        int id = createTicket();

        mockMvc.perform(get("/api/v1/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Email bounce"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee").value("Riley"))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andExpect(jsonPath("$.allowedTransitions[0]").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].category").value("billing"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/v1/tickets/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Email bounce — billing","assignee":" "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Email bounce — billing"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee").value(nullValue()));

        mockMvc.perform(post("/api/v1/tickets/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"author":"Jordan","body":"SPF record added"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comments[0].author").value("Jordan"))
                .andExpect(jsonPath("$.comments[0].body").value("SPF record added"));

        mockMvc.perform(post("/api/v1/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.comments[0].body").value("SPF record added"));
    }

    private int createTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Email bounce",
                                  "description":"Outbound mail is bouncing",
                                  "priority":"HIGH",
                                  "assignee":"Riley",
                                  "category":"billing"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }
}
