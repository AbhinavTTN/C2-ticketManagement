package com.company.ticketmanagement.ticket.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false, length = 120)
    private String author;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(String author, String body, Instant createdAt) {
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Comment(String author, String body) {
        this(author, body, Instant.now());
    }

    void assignTo(Ticket ticket) {
        this.ticket = ticket;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticket == null ? null : ticket.getId();
    }

    public String getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
