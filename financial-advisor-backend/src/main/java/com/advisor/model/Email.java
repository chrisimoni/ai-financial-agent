package com.advisor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "emails")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(unique = true)
    private String gmailId;

    private String fromEmail;
    private String fromName;
    private String toEmail;

    @Column(length = 1000)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    private LocalDateTime sentAt;
    private LocalDateTime receivedAt;
    private LocalDateTime indexedAt;

    private boolean isRead = false;
    private boolean isImportant = false;

    public Email(User user, String gmailId, String fromEmail, String subject) {
        this.user = user;
        this.gmailId = gmailId;
        this.fromEmail = fromEmail;
        this.subject = subject;
    }
}
