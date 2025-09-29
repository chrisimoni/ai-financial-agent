package com.advisor.repository;

import com.advisor.model.Contact;
import com.advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByHubspotId(String hubspotId);

    List<Contact> findByUserOrderByNameAsc(User user);

    @Query("""
            SELECT c FROM Contact c
             WHERE c.user = :user
             AND ((:query IS NULL OR :query = ''
                   OR c.name LIKE LOWER(CONCAT('%', :query, '%')))
                   OR c.email LIKE LOWER(CONCAT('%', :query, '%'))
                   OR c.company LIKE LOWER(CONCAT('%', :query, '%'))
                   OR c.phone LIKE LOWER(CONCAT('%', :query, '%'))
                   OR c.notes LIKE LOWER(CONCAT('%', :query, '%'))
                               )
            """)
    List<Contact> searchContacts(User user, String query);

    long countByUser(User user);
}
