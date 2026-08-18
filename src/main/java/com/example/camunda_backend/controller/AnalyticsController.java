package com.example.camunda_backend.controller;

import com.example.camunda_backend.entity.TaskMirror;
import com.example.camunda_backend.repository.TaskMirrorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final TaskMirrorRepository taskRepository;

    public AnalyticsController(TaskMirrorRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        List<TaskMirror> allTasks = taskRepository.findAll();
        
        // 1. Calculate KPIs
        long totalTasks = allTasks.size();
        long completedTasks = allTasks.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count();
        long pendingTasks = totalTasks - completedTasks;

        // 2. Workload Distribution (Bar Chart): Count active tasks per assignee
        Map<String, Long> workload = allTasks.stream()
            .filter(t -> t.getAssignee() != null && !t.getAssignee().trim().isEmpty())
            .filter(t -> !"COMPLETED".equalsIgnoreCase(t.getStatus()))
            .collect(Collectors.groupingBy(TaskMirror::getAssignee, Collectors.counting()));

        // 3. Task Outcomes (Pie Chart): Count decisions on completed tasks
        Map<String, Long> decisions = allTasks.stream()
            .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()) && t.getCompletionDecision() != null)
            .collect(Collectors.groupingBy(TaskMirror::getCompletionDecision, Collectors.counting()));

        Map<String, Object> response = new HashMap<>();
        response.put("kpis", Map.of("total", totalTasks, "completed", completedTasks, "pending", pendingTasks));
        response.put("workload", workload);
        response.put("decisions", decisions);

        return ResponseEntity.ok(response);
    }
}