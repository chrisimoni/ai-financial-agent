package com.advisor.repository;

import com.advisor.model.VectorStore;
import com.advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorStoreRepository extends JpaRepository<VectorStore, Long> {

    List<VectorStore> findByUser(User user);

    List<VectorStore> findByUserId(Long userId);

    @Query(value = "SELECT * FROM vector_store WHERE user_id = :userId ORDER BY id LIMIT :limit",
            nativeQuery = true)
    List<VectorStore> findTopByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}