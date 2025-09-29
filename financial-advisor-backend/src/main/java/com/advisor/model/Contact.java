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
@Table(name = "contacts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String name;
    private String email;
    private String phone;

    private String hubspotId;
    private String company;
    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDateTime lastSyncAt;

    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public Contact(User user, String name, String email){
        this.user = user;
        this.name = name;
        this.email = email;
    }

}
