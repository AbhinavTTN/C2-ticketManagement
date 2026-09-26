package com.company.ticketmanagement.ticket.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.ticketmanagement.ticket.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            SELECT DISTINCT t FROM Ticket t
            LEFT JOIN FETCH t.comments c
            WHERE t.id = :id
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Optional<Ticket> findByIdWithComments(@Param("id") Long id);
}
