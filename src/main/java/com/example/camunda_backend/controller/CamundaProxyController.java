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
    private final com.example.camunda_backend.service.FormAuditService formAuditService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public CamundaProxyController(UserRepository userRepository,
                                  com.example.camunda_backend.service.TaskInterceptorService taskInterceptorService,
                                  com.example.camunda_backend.service.FormAuditService formAuditService,
                                  com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.taskInterceptorService = taskInterceptorService;
        this.formAuditService = formAuditService;
        this.objectMapper = objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
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

            // Record process instance in MySQL database
            if (response instanceof Map<?, ?> respMap) {
                try {
                    String piId = (String) respMap.get("id");
                    String defId = (String) respMap.get("definitionId");
                    String defKey = defId != null && defId.contains(":") ? defId.split(":")[0] : id;
                    Integer ver = null;
                    if (defId != null && defId.contains(":")) {
                        try { ver = Integer.parseInt(defId.split(":")[1]); } catch (Exception ignored) {}
                    }
                    String bKey = (String) respMap.get("businessKey");
                    formAuditService.recordProcessInstanceStart(piId, defKey, null, ver, bKey, starterUserId);
                } catch (Exception ex) {
                    logger.debug("Failed to record process start in audit DB: {}", ex.getMessage());
                }
            }

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

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<?> claimTask(@PathVariable String taskId,
                                       @RequestBody(required = false) Map<String, Object> payload,
                                       Authentication authentication) {
        try {
            String username = null;
            if (payload != null && payload.get("userId") != null) {
                username = (String) payload.get("userId");
            }
            if (username == null || username.isBlank()) {
                if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal p) {
                    username = p.getUsername();
                } else if (authentication != null) {
                    username = authentication.getName();
                }
            }
            taskInterceptorService.claimTask(taskId, username);
            return ResponseEntity.ok(Map.of("status", "Task claimed successfully", "taskId", taskId, "userId", username != null ? username : ""));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tasks/{taskId}/unclaim")
    public ResponseEntity<?> unclaimTask(@PathVariable String taskId,
                                         @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String groupId = (payload != null && payload.get("groupId") != null) ? String.valueOf(payload.get("groupId")) : null;
            taskInterceptorService.unclaimTask(taskId, groupId);
            return ResponseEntity.ok(Map.of("status", "Task unclaimed successfully", "taskId", taskId));
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
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return getCompletedTaskVariables(taskId);
            }
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return getCompletedTaskVariables(taskId);
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
    public ResponseEntity<?> submitTask(@PathVariable String taskId,
                                        @RequestBody(required = false) Map<String, Object> payload,
                                        Authentication authentication) {
        String submittedBy = "anonymous";
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            submittedBy = principal.getUsername();
        } else if (authentication != null) {
            submittedBy = authentication.getName();
        }

        // 1. Fetch task info snapshot BEFORE completing (while task is active in Camunda)
        Map<String, Object> taskInfo = null;
        try {
            taskInfo = restTemplate.getForObject(camundaUrl + "/task/" + taskId, Map.class);
            if (taskInfo != null) {
                formAuditService.recordTaskCreatedOrUpdated(taskInfo);
            }
        } catch (Exception ignored) {}

        // 2. Fetch deployed form schema snapshot
        Object schema = null;
        try {
            schema = restTemplate.getForObject(camundaUrl + "/task/" + taskId + "/deployed-form", Object.class);
        } catch (Exception ignored) {}

        String formKey = (taskInfo != null && taskInfo.get("formKey") != null) ? (String) taskInfo.get("formKey") : null;
        String procInstId = (taskInfo != null && taskInfo.get("processInstanceId") != null) ? (String) taskInfo.get("processInstanceId") : null;
        Map<String, Object> body = (payload != null) ? payload : Map.of("variables", Map.of());

        // 3. Submit to Camunda engine
        ResponseEntity<?> camundaResponse;
        try {
            String url = camundaUrl + "/task/" + taskId + "/submit-form";
            Object response = restTemplate.postForObject(url, body, Object.class);
            camundaResponse = ResponseEntity.ok(response != null ? response : Map.of("status", "Task completed successfully"));
        } catch (Exception e) {
            // Fallback to /complete if submit-form fails or isn't applicable
            try {
                String completeUrl = camundaUrl + "/task/" + taskId + "/complete";
                restTemplate.postForLocation(completeUrl, body);
                camundaResponse = ResponseEntity.ok(Map.of("status", "Task completed successfully"));
            } catch (HttpStatusCodeException ex) {
                return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
            }
        }

        // 4. Persist form submission, variables, and completed task in MySQL database upon success
        try {
            formAuditService.recordFormSubmission(taskId, submittedBy, body, schema, formKey);
        } catch (Exception ex) {
            logger.warn("Failed to record form submission in audit DB for task {}: {}", taskId, ex.getMessage());
        }

        // 5. Check if process instance finished
        if (procInstId != null) {
            try {
                ResponseEntity<List<Map<String, Object>>> piCheck = restTemplate.exchange(
                        camundaUrl + "/history/process-instance?processInstanceId=" + procInstId,
                        HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                if (piCheck.getBody() != null && !piCheck.getBody().isEmpty()) {
                    Map<String, Object> histPi = piCheck.getBody().get(0);
                    String state = (String) histPi.get("state");
                    if ("COMPLETED".equalsIgnoreCase(state)) {
                        Object durObj = histPi.get("durationInMillis");
                        Long dur = durObj instanceof Number ? ((Number) durObj).longValue() : null;
                        formAuditService.recordProcessInstanceCompletion(procInstId, java.time.LocalDateTime.now(), dur, "COMPLETED");
                    }
                }
            } catch (Exception ignored) {}
        }

        return camundaResponse;
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

    @GetMapping("/tasks/completed/{taskId}/variables")
    public ResponseEntity<?> getCompletedTaskVariables(@PathVariable String taskId) {
        try {
            // Check local MySQL database first (fast & persistent)
            List<com.example.camunda_backend.entity.CustomFormVariable> localVars = formAuditService.getVariablesForTask(taskId);
            if (localVars != null && !localVars.isEmpty()) {
                List<Map<String, Object>> varList = new ArrayList<>();
                for (com.example.camunda_backend.entity.CustomFormVariable v : localVars) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", v.getVariableName());
                    map.put("type", v.getVariableType());
                    map.put("value", parseDbVarValue(v.getVariableValue(), v.getVariableType()));
                    varList.add(map);
                }
                return ResponseEntity.ok(varList);
            }

            // 1. Resolve task to obtain its processInstanceId from Camunda
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

    // ========================================================================
    // COMPLETED PROCESS INSTANCES & AUDIT TRAIL
    // ========================================================================

    @GetMapping({"/instances", "/instances/completed"})
    public ResponseEntity<?> getCompletedInstances(
            @RequestParam(required = false) String processDefinitionKey,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        try {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));

            String username = "anonymous";
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                username = principal.getUsername();
            } else if (authentication != null) {
                username = authentication.getName();
            }

            boolean activeOnly = "ACTIVE".equalsIgnoreCase(status);
            boolean finishedOnly = "COMPLETED".equalsIgnoreCase(status);

            Map<String, String> userNames = taskInterceptorService.fetchUserNamesMap();

            // 1. Fetch starter variable map (processInstanceId -> starter username)
            Map<String, String> starterVarMap = new HashMap<>();
            try {
                String varUrl = camundaUrl + "/history/variable-instance?variableName=starterUserId";
                ResponseEntity<List<Map<String, Object>>> varResp = restTemplate.exchange(
                        varUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                if (varResp.getBody() != null) {
                    for (Map<String, Object> v : varResp.getBody()) {
                        String piId = (String) v.get("processInstanceId");
                        String val = (String) v.get("value");
                        if (piId != null && val != null) {
                            starterVarMap.put(piId, val);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 2. Build process definition readable names map
            Map<String, String> procDefNames = new HashMap<>();
            try {
                String procUrl = camundaUrl + "/process-definition";
                ResponseEntity<List<Map<String, Object>>> procResp = restTemplate.exchange(
                        procUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                if (procResp.getBody() != null) {
                    for (Map<String, Object> pd : procResp.getBody()) {
                        String k = (String) pd.get("key");
                        String n = (String) pd.get("name");
                        if (k != null && n != null && !n.isBlank()) {
                            procDefNames.putIfAbsent(k, n);
                        }
                    }
                }
            } catch (Exception ignored) {}

            List<Map<String, Object>> instances = new ArrayList<>();

            if (isAdmin) {
                // Admins see all instances (active + completed, or filtered by status)
                StringBuilder urlBuilder = new StringBuilder(camundaUrl);
                urlBuilder.append("/history/process-instance?sortBy=startTime&sortOrder=desc");
                if (activeOnly) {
                    urlBuilder.append("&active=true");
                } else if (finishedOnly) {
                    urlBuilder.append("&finished=true");
                }
                if (processDefinitionKey != null && !processDefinitionKey.isBlank()) {
                    urlBuilder.append("&processDefinitionKey=").append(processDefinitionKey.trim());
                }
                ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                        urlBuilder.toString(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                if (resp.getBody() != null) {
                    instances.addAll(resp.getBody());
                }
            } else {
                // Regular users see instances they started OR participated in / have tasks in
                Set<String> instanceIds = new HashSet<>();

                // Instances started by username directly in Camunda
                try {
                    String url = camundaUrl + "/history/process-instance?startedBy=" + username;
                    if (activeOnly) url += "&active=true";
                    else if (finishedOnly) url += "&finished=true";
                    if (processDefinitionKey != null && !processDefinitionKey.isBlank()) {
                        url += "&processDefinitionKey=" + processDefinitionKey.trim();
                    }
                    ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                            url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                    if (resp.getBody() != null) {
                        for (Map<String, Object> pi : resp.getBody()) {
                            instanceIds.add((String) pi.get("id"));
                        }
                    }
                } catch (Exception ignored) {}

                // Instances started by username recorded in starterUserId variable
                for (Map.Entry<String, String> entry : starterVarMap.entrySet()) {
                    if (username.equalsIgnoreCase(entry.getValue())) {
                        instanceIds.add(entry.getKey());
                    }
                }

                // Instances where the user has at least one task (active or completed)
                try {
                    String taskUrl = camundaUrl + "/history/task?taskAssignee=" + username;
                    ResponseEntity<List<Map<String, Object>>> taskResp = restTemplate.exchange(
                            taskUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                    if (taskResp.getBody() != null) {
                        for (Map<String, Object> t : taskResp.getBody()) {
                            String pi = (String) t.get("processInstanceId");
                            if (pi != null) instanceIds.add(pi);
                        }
                    }
                } catch (Exception ignored) {}

                if (!instanceIds.isEmpty()) {
                    Map<String, Object> queryBody = new HashMap<>();
                    queryBody.put("processInstanceIds", new ArrayList<>(instanceIds));
                    if (activeOnly) {
                        queryBody.put("active", true);
                    } else if (finishedOnly) {
                        queryBody.put("finished", true);
                    }
                    queryBody.put("sorting", List.of(Map.of("sortBy", "startTime", "sortOrder", "desc")));
                    if (processDefinitionKey != null && !processDefinitionKey.isBlank()) {
                        queryBody.put("processDefinitionKey", processDefinitionKey.trim());
                    }

                    try {
                        ResponseEntity<List<Map<String, Object>>> postResp = restTemplate.exchange(
                                camundaUrl + "/history/process-instance",
                                HttpMethod.POST,
                                new HttpEntity<>(queryBody),
                                new ParameterizedTypeReference<>() {}
                        );
                        if (postResp.getBody() != null) {
                            instances.addAll(postResp.getBody());
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Enrich instances with startUserName, fallback starterUserId, duration, and state
            for (Map<String, Object> inst : instances) {
                String instId = (String) inst.get("id");
                String startUser = (String) inst.get("startUserId");
                if (startUser == null || startUser.isBlank()) {
                    startUser = starterVarMap.get(instId);
                }
                if (startUser == null || startUser.isBlank()) {
                    startUser = "System";
                }
                inst.put("startUserId", startUser);
                inst.put("startUserName", userNames.getOrDefault(startUser, startUser));

                String defKey = (String) inst.get("processDefinitionKey");
                String defName = (String) inst.get("processDefinitionName");
                if (defName == null || defName.isBlank()) {
                    inst.put("processDefinitionName", procDefNames.getOrDefault(defKey, defKey));
                }

                // Duration calculation: fix N/A for both completed and running instances
                Number dur = (Number) inst.get("durationInMillis");
                if (dur != null) {
                    inst.put("duration", dur.longValue());
                } else {
                    String startStr = (String) inst.get("startTime");
                    if (startStr != null) {
                        try {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                            long startMs = sdf.parse(startStr).getTime();
                            long elapsed = Math.max(0, System.currentTimeMillis() - startMs);
                            inst.put("duration", elapsed);
                        } catch (Exception ex) {
                            try {
                                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(startStr);
                                long elapsed = Math.max(0, java.time.Duration.between(odt, java.time.OffsetDateTime.now()).toMillis());
                                inst.put("duration", elapsed);
                            } catch (Exception ignored) {
                                inst.put("duration", null);
                            }
                        }
                    } else {
                        inst.put("duration", null);
                    }
                }

                String st = (String) inst.get("state");
                if (st == null || st.isBlank()) {
                    st = (inst.get("endTime") == null) ? "ACTIVE" : "COMPLETED";
                    inst.put("state", st);
                }
            }

            // Sort by startTime descending so newest active and completed instances appear first
            instances.sort((a, b) -> {
                String startA = (String) a.get("startTime");
                String startB = (String) b.get("startTime");
                if (startA == null && startB == null) return 0;
                if (startA == null) return 1;
                if (startB == null) return -1;
                return startB.compareTo(startA);
            });

            return ResponseEntity.ok(instances);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/instances/{instanceId}/tasks")
    public ResponseEntity<?> getInstanceTasks(@PathVariable String instanceId) {
        try {
            String url = camundaUrl + "/history/task?processInstanceId=" + instanceId + "&sortBy=startTime&sortOrder=asc";
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

    @GetMapping("/tasks/{taskId}/history-form")
    public ResponseEntity<?> getTaskHistoryForm(@PathVariable String taskId) {
        try {
            // 1. Check local MySQL database first (instant & reliable)
            Optional<com.example.camunda_backend.entity.CustomFormSubmission> subOpt = formAuditService.getFormSubmissionForTask(taskId);
            if (subOpt.isPresent()) {
                com.example.camunda_backend.entity.CustomFormSubmission sub = subOpt.get();
                Map<String, Object> result = new HashMap<>();
                result.put("taskId", sub.getTaskId());
                result.put("taskDefinitionKey", sub.getTaskDefinitionKey());
                result.put("submittedBy", sub.getSubmittedBy());
                result.put("submittedAt", sub.getSubmittedAt());
                result.put("hasSchema", sub.getFormSchemaJson() != null && !sub.getFormSchemaJson().isBlank());
                if (sub.getFormSchemaJson() != null) {
                    try {
                        result.put("schema", objectMapper.readValue(sub.getFormSchemaJson(), Object.class));
                    } catch (Exception e) {
                        result.put("schema", sub.getFormSchemaJson());
                    }
                }
                if (sub.getFormDataJson() != null) {
                    try {
                        result.put("data", objectMapper.readValue(sub.getFormDataJson(), Object.class));
                    } catch (Exception e) {
                        result.put("data", Collections.emptyMap());
                    }
                }
                return ResponseEntity.ok(result);
            }

            // 2. Fallback to Camunda REST history for legacy tasks
            String taskUrl = camundaUrl + "/history/task?taskId=" + taskId;
            ResponseEntity<List<Map<String, Object>>> taskResp = restTemplate.exchange(
                    taskUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            List<Map<String, Object>> taskList = taskResp.getBody();
            if (taskList == null || taskList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found in history"));
            }
            Map<String, Object> task = taskList.get(0);
            String procDefId = (String) task.get("processDefinitionId");
            String taskDefKey = (String) task.get("taskDefinitionKey");
            String procInstId = (String) task.get("processInstanceId");

            // 3. Get submitted variables for this process instance
            Map<String, Object> submittedData = new HashMap<>();
            if (procInstId != null) {
                try {
                    String varUrl = camundaUrl + "/history/variable-instance?processInstanceId=" + procInstId;
                    ResponseEntity<List<Map<String, Object>>> varResp = restTemplate.exchange(
                            varUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                    if (varResp.getBody() != null) {
                        for (Map<String, Object> v : varResp.getBody()) {
                            submittedData.put((String) v.get("name"), v.get("value"));
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 4. Resolve BPMN XML and find formKey/formRef
            String formResourceName = null;
            if (procDefId != null && taskDefKey != null) {
                try {
                    String xmlUrl = camundaUrl + "/process-definition/" + procDefId + "/xml";
                    Map<String, Object> xmlResp = restTemplate.getForObject(xmlUrl, Map.class);
                    if (xmlResp != null && xmlResp.get("bpmn20Xml") != null) {
                        String xml = (String) xmlResp.get("bpmn20Xml");
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<(?:bpmn:)?userTask[^>]*id=\"" + java.util.regex.Pattern.quote(taskDefKey) + "\"[^>]*>");
                        java.util.regex.Matcher m = p.matcher(xml);
                        if (m.find()) {
                            String tag = m.group(0);
                            java.util.regex.Matcher fk = java.util.regex.Pattern.compile("camunda:formKey=\"([^\"]+)\"").matcher(tag);
                            if (fk.find()) {
                                String rawKey = fk.group(1);
                                formResourceName = rawKey.contains(":") ? rawKey.substring(rawKey.lastIndexOf(':') + 1) : rawKey;
                            } else {
                                java.util.regex.Matcher fr = java.util.regex.Pattern.compile("camunda:formRef=\"([^\"]+)\"").matcher(tag);
                                if (fr.find()) {
                                    formResourceName = fr.group(1) + ".form";
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 5. Fetch deployed form schema from deployment resources
            Object formSchema = null;
            if (formResourceName != null && procDefId != null) {
                try {
                    String defUrl = camundaUrl + "/process-definition/" + procDefId;
                    Map<String, Object> defResp = restTemplate.getForObject(defUrl, Map.class);
                    if (defResp != null && defResp.get("deploymentId") != null) {
                        String deploymentId = (String) defResp.get("deploymentId");
                        String resUrl = camundaUrl + "/deployment/" + deploymentId + "/resources";
                        ResponseEntity<List<Map<String, Object>>> resResp = restTemplate.exchange(
                                resUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
                        if (resResp.getBody() != null) {
                            for (Map<String, Object> r : resResp.getBody()) {
                                String name = (String) r.get("name");
                                if (name != null && (name.equals(formResourceName) || name.endsWith("/" + formResourceName))) {
                                    String dataUrl = camundaUrl + "/deployment/" + deploymentId + "/resources/" + r.get("id") + "/data";
                                    byte[] bytes = restTemplate.getForObject(dataUrl, byte[].class);
                                    if (bytes != null && bytes.length > 0) {
                                        try {
                                            formSchema = new com.fasterxml.jackson.databind.ObjectMapper().readValue(bytes, Object.class);
                                        } catch (Exception ex) {
                                            formSchema = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            Map<String, Object> result = new HashMap<>();
            result.put("task", task);
            result.put("hasSchema", formSchema != null);
            result.put("schema", formSchema);
            result.put("data", submittedData);

            return ResponseEntity.ok(result);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private Object parseDbVarValue(String valStr, String type) {
        if (valStr == null) return null;
        if ("Boolean".equalsIgnoreCase(type)) {
            return Boolean.parseBoolean(valStr);
        }
        if ("Long".equalsIgnoreCase(type) || "Integer".equalsIgnoreCase(type)) {
            try { return Long.parseLong(valStr); } catch (Exception ignored) {}
        }
        if ("Double".equalsIgnoreCase(type)) {
            try { return Double.parseDouble(valStr); } catch (Exception ignored) {}
        }
        if ("Json".equalsIgnoreCase(type) || (valStr.startsWith("{") && valStr.endsWith("}")) || (valStr.startsWith("[") && valStr.endsWith("]"))) {
            try { return objectMapper.readValue(valStr, Object.class); } catch (Exception ignored) {}
        }
        return valStr;
    }
}
