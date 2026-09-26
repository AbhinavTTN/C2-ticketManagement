package com.company.ticketmanagement.ticket.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.ticketmanagement.ticket.domain.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicket_IdOrderByCreatedAtAscIdAsc(Long ticketId);
}
