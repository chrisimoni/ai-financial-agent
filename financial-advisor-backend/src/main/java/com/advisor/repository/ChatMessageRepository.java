package com.advisor.repository;

import com.advisor.model.ChatMessage;
import com.advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserAndSessionIdOrderByTimestampDesc(User user, String sessionId);
    long countByUser(User user);

    List<ChatMessage> findByUserAndSessionIdOrderByTimestampAsc(User user, String sessionId);

    @Query("SELECT cm.sessionId FROM ChatMessage cm WHERE cm.user.id = :userId GROUP BY cm.sessionId ORDER BY MAX(cm.timestamp) DESC")
    List<String> findDistinctSessionIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.user.id = :userId AND cm.sessionId = :sessionId ORDER BY cm.timestamp ASC")
    List<ChatMessage> findByUserIdAndSessionIdOrderByTimestampAsc(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
