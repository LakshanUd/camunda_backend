package com.example.camunda_backend.controller;

import com.example.camunda_backend.entity.User;
import com.example.camunda_backend.repository.UserRepository;
import com.example.camunda_backend.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CamundaProxyController {

    private static final Logger logger = LoggerFactory.getLogger(CamundaProxyController.class);

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final UserRepository userRepository;
    private final com.example.camunda_backend.service.TaskInterceptorService taskInterceptorService;
    private final RestTemplate restTemplate = new RestTemplate();

    public CamundaProxyController(UserRepository userRepository,
                                  com.example.camunda_backend.service.TaskInterceptorService taskInterceptorService) {
        this.userRepository = userRepository;
        this.taskInterceptorService = taskInterceptorService;
    }

    // ========================================================================
    // PROCESS DEFINITION & EXECUTION PROXIES
    // ========================================================================

    @GetMapping("/processes")
    public ResponseEntity<?> getProcesses() {
        try {
            String url = camundaUrl + "/process-definition?latestVersion=true&sortBy=name&sortOrder=asc";
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response != null ? response : Collections.emptyList());
        } catch (HttpStatusCodeException e) {
            logger.error("Camunda Engine error on getProcesses [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("Error proxying process definitions: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/processes/{id}/xml")
    public ResponseEntity<?> getProcessXml(@PathVariable String id) {
        try {
            String url = camundaUrl + "/process-definition/" + id + "/xml";
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/processes/{id}/instances/count")
    public ResponseEntity<?> getInstanceCount(@PathVariable String id) {
        try {
            String url = camundaUrl + "/process-instance/count?processDefinitionId=" + id;
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/processes/{id}/start")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> startProcess(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, Object> payload,
                                         Authentication authentication) {
        try {
            String url = camundaUrl + "/process-definition/" + id + "/start";

            // Inject authenticated user as 'starterUserId' variable for STAR routing strategy.
            // The TaskInterceptorService reads this variable on task.create events.
            String starterUserId = "anonymous";
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
                starterUserId = ((CustomUserPrincipal) authentication.getPrincipal()).getUsername();
            }

            Map<String, Object> requestBody = (payload != null) ? new HashMap<>(payload) : new HashMap<>();
            Map<String, Object> variables = (Map<String, Object>) requestBody.computeIfAbsent("variables", k -> new HashMap<>());

            // Set both variable names for maximum compatibility
            variables.putIfAbsent("starterUserId", Map.of("value", starterUserId, "type", "String"));
            variables.putIfAbsent("initiator",     Map.of("value", starterUserId, "type", "String"));

            Object response = restTemplate.postForObject(url, requestBody, Object.class);

            // Immediately trigger assignment engine so the first task gets assigned with 0 delay
            try {
                taskInterceptorService.dispatchUnassignedTasks();
            } catch (Exception ignored) {}

            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException e) {
            logger.error("Camunda start process error: {}", e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ========================================================================
    // TASK PROXIES WITH INTERCEPTION & ROUTING
    // ========================================================================

    @GetMapping("/tasks/active")
    public ResponseEntity<?> getActiveTasks(HttpServletRequest request) {
        try {
            // Trigger assignment engine before querying so freshly created tasks are routed
            try {
                taskInterceptorService.dispatchUnassignedTasks();
            } catch (Exception ignored) {}

            String queryString = request.getQueryString();
            String url = camundaUrl + "/task" + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "?sortBy=created&sortOrder=desc");

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> tasks = response.getBody();
            if (tasks == null) tasks = Collections.emptyList();
            return ResponseEntity.ok(taskInterceptorService.enrichTasks(tasks));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/my-tasks")
    public ResponseEntity<?> getMyTasks(
            @RequestParam(required = false) String userId,
            Authentication authentication) {
        try {
            try {
                taskInterceptorService.dispatchUnassignedTasks();
            } catch (Exception ignored) {}

            String effectiveUserId = userId;
            if (effectiveUserId == null || effectiveUserId.isBlank()) {
                if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
                    effectiveUserId = ((CustomUserPrincipal) authentication.getPrincipal()).getUsername();
                }
            } else {
                // If effectiveUserId is a UUID, resolve it to username (Camunda assigns by username)
                effectiveUserId = userRepository.findById(effectiveUserId)
                        .map(User::getUsername)
                        .orElse(effectiveUserId);
            }

            if (effectiveUserId == null || effectiveUserId.isBlank()) {
                effectiveUserId = "anonymous";
            }

            List<Map<String, Object>> tasks = taskInterceptorService.getTasksForUser(effectiveUserId);
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            logger.error("Error retrieving user tasks: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/users")
    public ResponseEntity<?> getActiveUsers() {
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        List<Map<String, Object>> result = activeUsers.stream()
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("username", u.getUsername());
                    map.put("fullName", u.getFullName());
                    map.put("email", u.getEmail());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tasks/unassigned")
    public ResponseEntity<?> getUnassignedTasks(@RequestParam(required = false) String group) {
        try {
            String url = camundaUrl + "/task?unassigned=true&sortBy=created&sortOrder=desc";
            if (group != null && !group.isBlank()) {
                url += "&candidateGroup=" + group;
            }

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> tasks = response.getBody();
            if (tasks == null) tasks = Collections.emptyList();
            return ResponseEntity.ok(taskInterceptorService.enrichTasks(tasks));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tasks/{taskId}/assign")
    public ResponseEntity<?> assignTask(@PathVariable String taskId, @RequestBody Map<String, Object> payload) {
        try {
            boolean unassign = Boolean.TRUE.equals(payload.get("unassign"))
                    || "UNASSIGN".equalsIgnoreCase(String.valueOf(payload.get("type")));
            String targetType = (String) payload.get("type");

            if (unassign) {
                taskInterceptorService.unassignTask(taskId);
                return ResponseEntity.ok(Map.of("status", "Task unassigned successfully", "taskId", taskId));
            }

            String groupId = (String) payload.get("groupId");
            if (groupId == null || groupId.isBlank()) {
                groupId = (String) payload.get("targetGroupId");
            }

            if ((groupId != null && !groupId.isBlank()) || "GROUP".equalsIgnoreCase(targetType)) {
                if (groupId != null && !groupId.isBlank()) {
                    taskInterceptorService.assignTaskToGroup(taskId, groupId);
                    return ResponseEntity.ok(Map.of("status", "Task assigned to group " + groupId, "taskId", taskId));
                }
            }

            String userId = (String) payload.get("userId");
            if (userId == null || userId.isBlank()) {
                userId = (String) payload.get("targetUserId");
            }

            if (userId != null && !userId.isBlank()) {
                taskInterceptorService.assignTaskToUser(taskId, userId);
                return ResponseEntity.ok(Map.of("status", "Task assigned to user " + userId, "taskId", taskId));
            }

            // Fallback: If payload was sent with empty userId or explicit unassign intent
            if (payload.containsKey("userId") && (userId == null || userId.isBlank())) {
                taskInterceptorService.unassignTask(taskId);
                return ResponseEntity.ok(Map.of("status", "Task unassigned successfully", "taskId", taskId));
            }

            return ResponseEntity.badRequest().body(Map.of("error", "Must provide either 'userId', 'groupId', or 'unassign: true'"));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/{taskId}/variables")
    public ResponseEntity<?> getTaskVariables(@PathVariable String taskId) {
        try {
            String url = camundaUrl + "/task/" + taskId + "/variables";
            Object variables = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(variables != null ? variables : Collections.emptyMap());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping({"/tasks/{taskId}/form-schema", "/tasks/{taskId}/deployed-form"})
    public ResponseEntity<?> getFormSchema(@PathVariable String taskId) {
        try {
            String url = camundaUrl + "/task/" + taskId + "/deployed-form";
            Object schema = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(schema != null ? schema : Collections.emptyMap());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", "No deployed form schema found for task " + taskId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping({"/tasks/{taskId}/submit", "/tasks/{taskId}/complete"})
    public ResponseEntity<?> submitTask(@PathVariable String taskId, @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String url = camundaUrl + "/task/" + taskId + "/submit-form";
            Map<String, Object> body = (payload != null) ? payload : Map.of("variables", Map.of());
            Object response = restTemplate.postForObject(url, body, Object.class);
            return ResponseEntity.ok(response != null ? response : Map.of("status", "Task completed successfully"));
        } catch (Exception e) {
            // Fallback to /complete if submit-form fails or isn't applicable
            try {
                String completeUrl = camundaUrl + "/task/" + taskId + "/complete";
                Map<String, Object> body = (payload != null) ? payload : Map.of("variables", Map.of());
                restTemplate.postForLocation(completeUrl, body);
                return ResponseEntity.ok(Map.of("status", "Task completed successfully"));
            } catch (HttpStatusCodeException ex) {
                return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
            }
        }
    }

    @GetMapping("/tasks/completed")
    public ResponseEntity<?> getCompletedTasks(HttpServletRequest request, Authentication authentication) {
        try {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));

            StringBuilder urlBuilder = new StringBuilder(camundaUrl);
            urlBuilder.append("/history/task?finished=true&sortBy=endTime&sortOrder=desc");

            if (!isAdmin) {
                String username = "anonymous";
                if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                    username = principal.getUsername();
                } else if (authentication != null) {
                    username = authentication.getName();
                }
                urlBuilder.append("&taskAssignee=").append(username);
            }

            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isBlank()) {
                String[] params = queryString.split("&");
                for (String param : params) {
                    if (!param.startsWith("finished=") && !param.startsWith("sortBy=") && !param.startsWith("sortOrder=")) {
                        if (isAdmin || !param.startsWith("taskAssignee=")) {
                            urlBuilder.append("&").append(param);
                        }
                    }
                }
            }

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    urlBuilder.toString(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> tasks = response.getBody();
            if (tasks == null) tasks = Collections.emptyList();

            return ResponseEntity.ok(taskInterceptorService.enrichTasks(tasks));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping({"/tasks/completed/{taskId}/variables", "/tasks/{taskId}/variables"})
    public ResponseEntity<?> getCompletedTaskVariables(@PathVariable String taskId) {
        try {
            // 1. Resolve task to obtain its processInstanceId
            String taskUrl = camundaUrl + "/history/task?taskId=" + taskId;
            ResponseEntity<List<Map<String, Object>>> taskResp = restTemplate.exchange(
                    taskUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> taskList = taskResp.getBody();
            String processInstanceId = null;
            if (taskList != null && !taskList.isEmpty()) {
                processInstanceId = (String) taskList.get(0).get("processInstanceId");
            }

            // Fallback for active tasks
            if (processInstanceId == null) {
                try {
                    Map<String, Object> activeTask = restTemplate.getForObject(camundaUrl + "/task/" + taskId, Map.class);
                    if (activeTask != null) {
                        processInstanceId = (String) activeTask.get("processInstanceId");
                    }
                } catch (Exception ignored) {}
            }

            if (processInstanceId == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // 2. Query variable instances for this specific process instance ONLY
            String varUrl = camundaUrl + "/history/variable-instance?processInstanceId=" + processInstanceId;
            ResponseEntity<List<Map<String, Object>>> varResp = restTemplate.exchange(
                    varUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> variables = varResp.getBody();
            return ResponseEntity.ok(variables != null ? variables : Collections.emptyList());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping({"/tasks/completed/{taskId}/details", "/tasks/{taskId}/details"})
    public ResponseEntity<?> getTaskDetails(@PathVariable String taskId) {
        try {
            String taskUrl = camundaUrl + "/history/task?taskId=" + taskId;
            ResponseEntity<List<Map<String, Object>>> taskResp = restTemplate.exchange(
                    taskUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> taskList = taskResp.getBody();
            Map<String, Object> taskData = (taskList != null && !taskList.isEmpty()) ? new HashMap<>(taskList.get(0)) : new HashMap<>();

            if (taskData.isEmpty()) {
                try {
                    Map<String, Object> activeTask = restTemplate.getForObject(camundaUrl + "/task/" + taskId, Map.class);
                    if (activeTask != null) {
                        taskData.putAll(activeTask);
                    }
                } catch (Exception ignored) {}
            }

            if (!taskData.isEmpty()) {
                List<Map<String, Object>> single = new ArrayList<>();
                single.add(taskData);
                taskInterceptorService.enrichTasks(single);
                taskData = single.get(0);
            }

            String processInstanceId = (String) taskData.get("processInstanceId");
            List<Map<String, Object>> variables = Collections.emptyList();
            if (processInstanceId != null) {
                String varUrl = camundaUrl + "/history/variable-instance?processInstanceId=" + processInstanceId;
                ResponseEntity<List<Map<String, Object>>> varResp = restTemplate.exchange(
                        varUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {}
                );
                if (varResp.getBody() != null) {
                    variables = varResp.getBody();
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("task", taskData);
            result.put("variables", variables);
            return ResponseEntity.ok(result);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
