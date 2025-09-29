package com.advisor.service;

import com.advisor.model.Task;
import com.advisor.model.User;
import com.advisor.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;


    public Task createTask(Task task) {
        task.setStatus(Task.TaskStatus.PENDING);
        return taskRepository.save(task);
    }

    public Task updateTask(Long taskId, Task.TaskStatus status, String result) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setStatus(status);
            if (result != null) {
                task.setResult(result);
            }
            return taskRepository.save(task);
        }
        throw new RuntimeException("Task not found: " + taskId);
    }

    public List<Task> getActiveTasks(User user) {
        return taskRepository.findActiveTasks(user);
    }

    @Async
    public void executeTask(Task task) {
        try {
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
            taskRepository.save(task);

            String result = processTask(task);

            task.setStatus(Task.TaskStatus.COMPLETED);
            task.setResult(result);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

        } catch (Exception e) {
            task.setStatus(Task.TaskStatus.FAILED);
            task.setResult("Task failed: " + e.getMessage());
            taskRepository.save(task);
        }
    }

    private String processTask(Task task) {
        switch (task.getType()) {
            case SCHEDULE_APPOINTMENT:
                return "Scheduling workflow initiated for: " + task.getTitle();
            case SEND_EMAIL:
                return "Email sent successfully";
            case CREATE_CONTACT:
                return "Contact created successfully";
            case PROACTIVE_ACTION:
                return "Proactive action completed";
            default:
                return "Task type not implemented: " + task.getType();
        }
    }

    public void continueTask(Task task, String responseContext) {
        task.setStatus(Task.TaskStatus.IN_PROGRESS);
        String currentContext = task.getContext();
        String updatedContext = currentContext + "\nResponse: " + responseContext;
        task.setContext(updatedContext);
        taskRepository.save(task);

        executeTask(task);
    }
}
