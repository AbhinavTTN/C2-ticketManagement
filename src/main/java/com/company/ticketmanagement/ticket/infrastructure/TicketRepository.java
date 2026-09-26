package com.company.ticketmanagement.ticket.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketStatus;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            SELECT DISTINCT t FROM Ticket t
            LEFT JOIN FETCH t.comments c
            WHERE t.id = :id
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Optional<Ticket> findByIdWithComments(@Param("id") Long id);

    @Query(
            value = """
                    SELECT t FROM Ticket t
                    WHERE (:status IS NULL OR t.status = :status)
                      AND (
                            :keyword IS NULL
                            OR LOWER(t.title) LIKE :keyword ESCAPE '\\'
                            OR LOWER(t.description) LIKE :keyword ESCAPE '\\'
                            OR EXISTS (
                                SELECT c.id FROM Comment c
                                WHERE c.ticket = t
                                  AND (
                                        LOWER(c.author) LIKE :keyword ESCAPE '\\'
                                        OR LOWER(c.body) LIKE :keyword ESCAPE '\\'
                                  )
                            )
                      )
                    """,
            countQuery = """
                    SELECT COUNT(t) FROM Ticket t
                    WHERE (:status IS NULL OR t.status = :status)
                      AND (
                            :keyword IS NULL
                            OR LOWER(t.title) LIKE :keyword ESCAPE '\\'
                            OR LOWER(t.description) LIKE :keyword ESCAPE '\\'
                            OR EXISTS (
                                SELECT c.id FROM Comment c
                                WHERE c.ticket = t
                                  AND (
                                        LOWER(c.author) LIKE :keyword ESCAPE '\\'
                                        OR LOWER(c.body) LIKE :keyword ESCAPE '\\'
                                  )
                            )
                      )
                    """)
    Page<Ticket> search(
            @Param("status") TicketStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
