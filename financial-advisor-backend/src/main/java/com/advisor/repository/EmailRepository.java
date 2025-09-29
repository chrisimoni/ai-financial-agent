package com.advisor.repository;

import com.advisor.model.Email;
import com.advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {


    Optional<Email> findByGmailId(String gmailId);


    @Query("SELECT e FROM Email e WHERE e.user = :user AND e.receivedAt >= :since ORDER BY e.receivedAt DESC")
    List<Email> findRecentEmails(@Param("user") User user, @Param("since") LocalDateTime since);


    @Query("SELECT e FROM Email e WHERE e.user = :user AND " +
            "(LOWER(e.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.body) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.fromEmail) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Email> searchEmails(@Param("user") User user, @Param("searchTerm") String searchTerm);


    List<Email> findByUserAndFromEmailOrderByReceivedAtDesc(User user, String fromEmail);


    @Query("SELECT e FROM Email e WHERE e.user = :user AND e.indexedAt IS NULL")
    List<Email> findUnindexedEmails(@Param("user") User user);

    long countByUser(User user);


    List<Email> findByUserOrderByReceivedAtDesc(User user);


    @Query("SELECT e FROM Email e WHERE e.user = :user AND e.isRead = false ORDER BY e.receivedAt DESC")
    List<Email> findUnreadEmails(@Param("user") User user);


    @Query("SELECT e FROM Email e WHERE e.user = :user AND e.isImportant = true ORDER BY e.receivedAt DESC")
    List<Email> findImportantEmails(@Param("user") User user);
}
