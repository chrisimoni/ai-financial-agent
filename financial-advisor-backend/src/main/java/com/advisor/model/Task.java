package com.advisor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    private TaskType type;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(columnDefinition = "TEXT")
    private String result;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;

    public enum TaskStatus {
        PENDING, IN_PROGRESS, WAITING_FOR_RESPONSE, COMPLETED, FAILED, CANCELLED
    }

    public enum TaskType {
        SCHEDULE_APPOINTMENT, SEND_EMAIL, CREATE_CONTACT, UPDATE_CONTACT,
        CALENDAR_EVENT, FOLLOW_UP, RESEARCH, PROACTIVE_ACTION
    }

    public Task(User user, String title, String description, TaskType type) {
        this.user = user;
        this.title = title;
        this.description = description;
        this.type = type;
        this.status = TaskStatus.PENDING;
    }
}
