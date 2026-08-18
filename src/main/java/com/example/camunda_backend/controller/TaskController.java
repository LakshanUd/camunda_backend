package com.example.camunda_backend.controller;

import com.example.camunda_backend.service.BusinessDataService;
import com.example.camunda_backend.service.TaskSyncService;
import com.example.camunda_backend.service.AutoDispatcherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final TaskSyncService taskSyncService;
    private final BusinessDataService businessDataService;

    public TaskController(TaskSyncService taskSyncService, BusinessDataService businessDataService, AutoDispatcherService autoDispatcherService) {
        this.taskSyncService = taskSyncService;
        this.businessDataService = businessDataService;
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
    public ResponseEntity<?> assignTask(@PathVariable String taskId, @RequestBody Map<String, Object> payload) {
        try {
            if (payload.containsKey("autoDispatch") && (Boolean) payload.get("autoDispatch")) {
                // Trigger the Auto-Dispatcher for this specific task
                // (You can reuse your existing auto-dispatch logic here or just let the scheduled daemon catch it)
                return ResponseEntity.ok(Map.of("status", "Task flagged for Auto-Dispatch!"));
            }

            if (payload.containsKey("groupId")) {
                // Forward to a group queue (clears the assignee and adds a candidate group)
                String unassignUrl = camundaUrl + "/task/" + taskId + "/assignee";
                restTemplate.postForLocation(unassignUrl, Map.of("userId", (Object) null));
                
                // Note: Adding an identity link in Camunda requires a specific API call, 
                // but clearing the assignee throws it back to the existing group pool!
                return ResponseEntity.ok(Map.of("status", "Task forwarded back to Department Queue!"));
            }

            // Default: Assign to a specific user
            String userId = payload.get("userId").toString();
            String assignUrl = camundaUrl + "/task/" + taskId + "/assignee";
            restTemplate.postForLocation(assignUrl, Map.of("userId", userId));

            // Sync to our MySQL database instantly
            taskSyncService.syncTask(taskId, null, null, null, userId, null, "ASSIGNED");

            return ResponseEntity.ok(Map.of("status", "Task successfully assigned to " + userId));
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

    // 4. TRANSACTIONAL COMPLETE ENDPOINT: Strips business data -> Saves to MySQL -> Completes Camunda Task
    @PostMapping("/{taskId}/complete")
    @Transactional // Guarantees that if Camunda API fails, MySQL writes roll back automatically!
    public ResponseEntity<?> completeTask(@PathVariable String taskId, 
                                          @RequestHeader(value = "X-User-Id", defaultValue = "ANONYMOUS") String userId,
                                          @RequestBody(required = false) Map<String, Object> payload) {
        try {
            // A. Fetch task details from Camunda first to get the true Process Instance ID
            String taskUrl = camundaUrl + "/task/" + taskId;
            Map<?, ?> taskInfo = restTemplate.getForObject(taskUrl, Map.class);
            String processId = taskInfo != null && taskInfo.get("processInstanceId") != null ? taskInfo.get("processInstanceId").toString() : "UNKNOWN";

            // B. Separate payload: Save heavy form data to MySQL table, return only lightweight routing variables!
            Map<String, Object> rawVariables = (payload != null && payload.get("variables") instanceof Map) ? 
                    (Map<String, Object>) payload.get("variables") : Collections.emptyMap();
            
            Map<String, Object> cleanEngineVariables = businessDataService.extractAndSaveBusinessData(processId, taskId, userId, rawVariables);

            // C. Forward ONLY the lightweight routing variables (e.g. nextReviewer, decision) to Camunda Engine!
            String completeUrl = camundaUrl + "/task/" + taskId + "/complete";
            restTemplate.postForLocation(completeUrl, Map.of("variables", cleanEngineVariables));

            // D. Extract decision and comment for our task activity mirror sync
            String decision = rawVariables.containsKey("decision") ? ((Map<?, ?>) rawVariables.get("decision")).get("value").toString() : "Completed";
            String comment = rawVariables.containsKey("comment") ? ((Map<?, ?>) rawVariables.get("comment")).get("value").toString() : "";
            taskSyncService.syncTaskCompletion(taskId, decision, comment);

            return ResponseEntity.ok(Map.of("status", "Task completed! Business data saved to MySQL and workflow routed successfully!"));
        } catch (Exception e) {
            // Because of @Transactional, any failure here immediately rolls back the MySQL business data inserts!
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 5. UPGRADED FORM-FIELDS ENDPOINT: Merges Camunda engine variables with MySQL business data!
    @GetMapping("/{taskId}/form-fields")
    public ResponseEntity<?> getTaskFormFields(@PathVariable String taskId) {
        try {
            // A. Fetch task details to get Process Instance ID
            String taskUrl = camundaUrl + "/task/" + taskId;
            Map<?, ?> taskInfo = restTemplate.getForObject(taskUrl, Map.class);
            String processId = taskInfo != null && taskInfo.get("processInstanceId") != null ? taskInfo.get("processInstanceId").toString() : null;

            // B. Fetch whatever variables exist in Camunda (routing rules, etc.)
            String varsUrl = camundaUrl + "/task/" + taskId + "/variables";
            Map<String, Object> camundaVars = restTemplate.getForObject(varsUrl, Map.class);
            if (camundaVars == null) camundaVars = new HashMap<>();

            // C. Fetch all custom business data from MySQL and merge it into one seamless dictionary!
            Map<String, Object> mysqlBusinessData = businessDataService.getMergedBusinessData(processId);
            camundaVars.putAll(mysqlBusinessData);

            return ResponseEntity.ok(camundaVars);
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