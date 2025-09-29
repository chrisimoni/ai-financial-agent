package com.advisor.repository;

import com.advisor.model.Task;
import com.advisor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserAndStatus(User user, Task.TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.status IN ('PENDING', 'IN_PROGRESS', 'WAITING_FOR_RESPONSE')")
    List<Task> findActiveTasks(@Param("user") User user);

    @Query("SELECT t FROM Task t WHERE t.status = 'PENDING' AND t.scheduledAt IS NOT NULL AND t.scheduledAt <= :now")
    List<Task> findScheduledTasksReadyToExecute(@Param("now") LocalDateTime now);
}
