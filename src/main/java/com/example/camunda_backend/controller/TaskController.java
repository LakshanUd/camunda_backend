package com.example.camunda_backend.controller;

import com.example.camunda_backend.service.TaskSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final TaskSyncService taskSyncService;

    public TaskController(TaskSyncService taskSyncService) {
        this.taskSyncService = taskSyncService;
    }

    // Sandboxed Task Fetcher: Enforces RBAC query filtering
    @GetMapping("/my-tasks")
    public ResponseEntity<?> getTasks(
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "false") boolean isAdmin) {
        try {
            String url;

            if (isAdmin) {
                // Admin Override: Unfiltered global query for all active tasks
                url = camundaUrl + "/task?sortBy=created&sortOrder=desc";
            } else {
                // Strict Worker Sandbox: Only fetch tasks directly assigned to this identity
                url = camundaUrl + "/task?assignee=" + userId + "&sortBy=created&sortOrder=desc";
            }

            Object tasks = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(tasks != null ? tasks : Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Task Fetch Error: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 1. ADMIN QUEUE: Fetch unassigned tasks sitting in departmental holding pools
    @GetMapping("/unassigned")
    public ResponseEntity<?> getUnassignedTasks(@RequestParam(required = false) String group) {
        try {
            String url = camundaUrl + "/task?unassigned=true&sortBy=created&sortOrder=desc";
            if (group != null && !group.isEmpty()) {
                url += "&candidateGroup=" + group;
            }
            Object tasks = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(tasks != null ? tasks : Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. ADMIN DISPATCHER: Bind a task to a specific user (or unassign if userId is empty)
    @PostMapping("/{taskId}/assign")
    public ResponseEntity<?> assignTask(@PathVariable String taskId, @RequestBody Map<String, String> payload) {
        try {
            String targetUserId = payload.get("userId");
            String url = camundaUrl + "/task/" + taskId + "/assignee";
            
            // Camunda API expects {"userId": "name"} or {"userId": null} to unassign
            Map<String, Object> camundaPayload = new HashMap<>();
            camundaPayload.put("userId", (targetUserId != null && !targetUserId.trim().isEmpty()) ? targetUserId.trim() : null);
            
            restTemplate.postForLocation(url, camundaPayload);
            return ResponseEntity.ok(Map.of("status", "Task successfully assigned to " + targetUserId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. USER DICTIONARY: Fetch all Camunda users so the Admin can populate their assignment dropdown
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            String url = camundaUrl + "/user";
            Object users = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(users != null ? users : Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. WORKER COMPLETION: Complete a task and attach dynamic process variables for next stage routing
    @PostMapping("/{taskId}/complete")
    public ResponseEntity<?> completeTask(@PathVariable String taskId, @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String url = camundaUrl + "/task/" + taskId + "/complete";
            restTemplate.postForLocation(url, payload != null ? payload : Collections.emptyMap());

            // Extract decision and comment from payload variables if they exist
            String decision = "Completed";
            String comment = "";
            if (payload != null && payload.get("variables") instanceof Map) {
                Map<?, ?> vars = (Map<?, ?>) payload.get("variables");
                if (vars.containsKey("decision") && ((Map<?, ?>) vars.get("decision")).get("value") != null) {
                    decision = ((Map<?, ?>) vars.get("decision")).get("value").toString();
                }
                if (vars.containsKey("comment") && ((Map<?, ?>) vars.get("comment")).get("value") != null) {
                    comment = ((Map<?, ?>) vars.get("comment")).get("value").toString();
                }
            }

            // INSTANT MYSQL SYNC: Mark completed in custom database!
            taskSyncService.syncTaskCompletion(taskId, decision, comment);

            return ResponseEntity.ok(Map.of("status", "Task completed and synced to MySQL successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 6. NATIVE FORMS: Fetch official deployed Camunda JSON Form schema
    @GetMapping("/{taskId}/deployed-form")
    public ResponseEntity<?> getDeployedForm(@PathVariable String taskId) {
        try {
            String url = camundaUrl + "/task/" + taskId + "/deployed-form";
            Object formSchema = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(formSchema != null ? formSchema : Collections.emptyMap());
        } catch (Exception e) {
            // Returns 404 if no JSON form schema was attached in Modeler
            return ResponseEntity.status(404).body(Map.of("message", "No deployed form schema found"));
        }
    }
}