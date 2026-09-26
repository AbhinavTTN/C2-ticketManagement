package com.company.ticketmanagement.ticket.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketPriority priority;

    @Column(length = 120)
    private String assignee;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<Comment> comments = new ArrayList<>();

    protected Ticket() {
    }

    public Ticket(String title, String description, TicketPriority priority, String assignee, String category) {
        Instant now = Instant.now();
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assignee = assignee;
        this.category = category;
        this.status = TicketStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(
            String title,
            String description,
            TicketPriority priority,
            String assignee,
            String category) {
        boolean changed = false;
        if (title != null) {
            this.title = title;
            changed = true;
        }
        if (description != null) {
            this.description = description;
            changed = true;
        }
        if (priority != null) {
            this.priority = priority;
            changed = true;
        }
        if (assignee != null) {
            this.assignee = assignee.isBlank() ? null : assignee;
            changed = true;
        }
        if (category != null) {
            this.category = category;
            changed = true;
        }
        if (changed) {
            this.updatedAt = Instant.now();
        }
    }

    public void transitionTo(TicketStatus target) {
        TicketStateMachine.transition(this, target);
    }

    void applyStatus(TicketStatus target) {
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        comment.assignTo(this);
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getCategory() {
        return category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Comment> getComments() {
        return comments;
    }
}
