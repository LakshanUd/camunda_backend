package com.example.camunda_backend.controller;

import com.example.camunda_backend.entity.TaskRoutingRule;
import com.example.camunda_backend.entity.User;
import com.example.camunda_backend.entity.WorkflowAuthorization;
import com.example.camunda_backend.repository.TaskRoutingRuleRepository;
import com.example.camunda_backend.repository.UserRepository;
import com.example.camunda_backend.repository.WorkflowAuthorizationRepository;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =========================================================================
 * ASSIGNMENT CONTROLLER — REST API for the Workflow Assignment Engine
 * =========================================================================
 *
 * Manages workflow authorizations (assigning users and groups to workflows)
 * and task routing rules (STAR, DYNAMIC_USER, SELECT_USER, SELECT_GROUP).
 * Groups are sourced directly from the existing Groups Pool (/admin/groups).
 * Users are sourced from the custom MySQL identity database (app_users).
 *
 * Base URL: /api/assignments
 * Security: ADMIN role required for all endpoints.
 * =========================================================================
 */
@RestController
@RequestMapping("/api/assignments")
@PreAuthorize("hasRole('ADMIN')")
public class AssignmentController {

    private static final Logger log = LoggerFactory.getLogger(AssignmentController.class);

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final WorkflowAuthorizationRepository authRepo;
    private final TaskRoutingRuleRepository routingRuleRepo;
    private final UserRepository userRepo;
    private final com.example.camunda_backend.service.TaskInterceptorService taskInterceptorService;
    private final RestTemplate restTemplate = new RestTemplate();

    public AssignmentController(WorkflowAuthorizationRepository authRepo,
                                TaskRoutingRuleRepository routingRuleRepo,
                                UserRepository userRepo,
                                com.example.camunda_backend.service.TaskInterceptorService taskInterceptorService) {
        this.authRepo             = authRepo;
        this.routingRuleRepo      = routingRuleRepo;
        this.userRepo             = userRepo;
        this.taskInterceptorService = taskInterceptorService;
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /** Strips Camunda version suffix from process definition IDs. */
    private String normalizeKey(String id) {
        if (id == null) return null;
        return id.contains(":") ? id.split(":")[0] : id;
    }

    private ResponseEntity<?> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", msg));
    }

    private ResponseEntity<?> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private Map<String, String> fetchGroupNamesMap() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String url = camundaUrl + "/group";
            List<Map<String, Object>> groups = restTemplate.getForObject(url, List.class);
            if (groups != null) {
                for (Map<String, Object> g : groups) {
                    String id = (String) g.get("id");
                    String name = (String) g.get("name");
                    if (id != null) map.put(id, (name != null && !name.isBlank()) ? name : id);
                }
            }
        } catch (Exception e) {
            log.warn("[GROUPS] Failed to fetch group map from Camunda: {}", e.getMessage());
        }
        return map;
    }

    // =========================================================================
    // SECTION 1: CAMUNDA WORKFLOW PROXY ENDPOINTS
    // =========================================================================

    /**
     * GET /api/assignments/available-workflows
     * Fetches all deployed process definitions from Camunda (latest versions only).
     */
    @GetMapping("/available-workflows")
    public ResponseEntity<?> getAvailableWorkflows() {
        try {
            String url = camundaUrl + "/process-definition?latestVersion=true&sortBy=name&sortOrder=asc";
            List<?> defs = restTemplate.getForObject(url, List.class);
            return ResponseEntity.ok(defs != null ? defs : Collections.emptyList());
        } catch (Exception e) {
            log.warn("[WORKFLOWS] Camunda unavailable: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * GET /api/assignments/available-workflows/{id}/tasks
     * Parses the BPMN XML from Camunda to extract all UserTask elements.
     * The {id} can be either a process definition ID or a process key.
     */
    @GetMapping("/available-workflows/{id}/tasks")
    public ResponseEntity<?> getWorkflowTasks(@PathVariable String id) {
        String bpmnXml = fetchBpmnXml(id);
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        try {
            BpmnModelInstance model = Bpmn.readModelFromStream(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Collection<UserTask> tasks = model.getModelElementsByType(UserTask.class);

            List<Map<String, String>> taskList = tasks.stream()
                    .map(t -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("id",   t.getId());
                        m.put("name", t.getName() != null && !t.getName().isBlank() ? t.getName() : t.getId());
                        return m;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(taskList);
        } catch (Exception e) {
            log.error("[TASKS] Failed to parse BPMN for '{}': {}", id, e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    private String fetchBpmnXml(String id) {
        for (String path : List.of("/process-definition/" + id + "/xml",
                                   "/process-definition/key/" + id + "/xml")) {
            try {
                Map<?, ?> resp = restTemplate.getForObject(camundaUrl + path, Map.class);
                if (resp != null && resp.get("bpmn20Xml") != null) {
                    return resp.get("bpmn20Xml").toString();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // =========================================================================
    // SECTION 2: EXISTING GROUPS (from /admin/groups pool)
    // =========================================================================

    /**
     * GET /api/assignments/groups — Returns all existing groups from the group pool.
     * Sourced from the Camunda groups endpoint, matching /admin/groups exactly.
     */
    @GetMapping("/groups")
    public ResponseEntity<?> getAllGroups() {
        try {
            String url = camundaUrl + "/group?sortBy=name&sortOrder=asc";
            List<?> response = restTemplate.getForObject(url, List.class);
            return ResponseEntity.ok(response != null ? response : Collections.emptyList());
        } catch (Exception e) {
            log.error("[GROUPS] Failed to query groups: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    // =========================================================================
    // SECTION 3: WORKFLOW AUTHORIZATIONS (Users & Groups)
    // =========================================================================

    /**
     * GET /api/assignments/workflow/{workflowKey}/authorizations
     * Returns all user and group authorizations for a given workflow.
     */
    @GetMapping("/workflow/{workflowKey}/authorizations")
    public ResponseEntity<?> getWorkflowAuthorizations(@PathVariable String workflowKey) {
        String key = normalizeKey(workflowKey);
        List<WorkflowAuthorization> auths = authRepo.findByWorkflowKey(key);
        Map<String, String> groupNames = fetchGroupNamesMap();

        List<Map<String, Object>> result = auths.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          a.getId());
            m.put("workflowKey", a.getWorkflowKey());
            m.put("type",        a.isUserAuthorization() ? "USER" : "GROUP");
            if (a.isUserAuthorization()) {
                String uid = a.getUserId();
                User u = userRepo.findById(uid).or(() -> userRepo.findByUsername(uid)).orElse(null);
                m.put("userId",   uid);
                m.put("username", u != null ? u.getUsername() : uid);
                m.put("fullName", u != null ? u.getFullName() : uid);
            } else {
                String gId = a.getGroupId();
                m.put("groupId",   gId);
                m.put("groupName", groupNames.getOrDefault(gId, gId));
            }
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/assignments/workflow/{workflowKey}/authorizations/user
     * Authorizes a specific user to access a workflow.
     * Body: { "userId": "uuid-or-username" }
     */
    @PostMapping("/workflow/{workflowKey}/authorizations/user")
    public ResponseEntity<?> authorizeUser(@PathVariable String workflowKey,
                                           @RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        if (userId == null || userId.isBlank()) return badRequest("'userId' is required");

        String key = normalizeKey(workflowKey);
        if (authRepo.existsByWorkflowKeyAndUserId(key, userId)) {
            return ResponseEntity.ok(Map.of("message", "User is already authorized for this workflow"));
        }

        WorkflowAuthorization saved = authRepo.save(new WorkflowAuthorization(key, userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User authorized for workflow",
                "id",      saved.getId()
        ));
    }

    /**
     * POST /api/assignments/workflow/{workflowKey}/authorizations/group
     * Authorizes an entire group to access a workflow.
     * Body: { "groupId": "accounting" }
     */
    @PostMapping("/workflow/{workflowKey}/authorizations/group")
    public ResponseEntity<?> authorizeGroup(@PathVariable String workflowKey,
                                            @RequestBody Map<String, Object> body) {
        Object gIdObj = body.get("groupId");
        if (gIdObj == null || gIdObj.toString().isBlank()) return badRequest("'groupId' is required");
        String groupId = gIdObj.toString().trim();

        String key = normalizeKey(workflowKey);
        if (authRepo.existsByWorkflowKeyAndGroupId(key, groupId)) {
            return ResponseEntity.ok(Map.of("message", "Group is already authorized for this workflow"));
        }

        WorkflowAuthorization saved = authRepo.save(WorkflowAuthorization.forGroup(key, groupId));
        Map<String, String> groupNames = fetchGroupNamesMap();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message",   "Group authorized for workflow",
                "id",        saved.getId(),
                "groupId",   groupId,
                "groupName", groupNames.getOrDefault(groupId, groupId)
        ));
    }

    /** DELETE /api/assignments/workflow/{workflowKey}/authorizations/{authId} */
    @DeleteMapping("/workflow/{workflowKey}/authorizations/{authId}")
    public ResponseEntity<?> revokeAuthorization(@PathVariable String workflowKey,
                                                 @PathVariable Long authId) {
        authRepo.deleteById(authId);
        return ResponseEntity.ok(Map.of("message", "Authorization revoked"));
    }

    /** DELETE /api/assignments/workflow/{workflowKey}/authorizations/user/{userId} */
    @DeleteMapping("/workflow/{workflowKey}/authorizations/user/{userId}")
    public ResponseEntity<?> revokeUserAuthorization(@PathVariable String workflowKey,
                                                     @PathVariable String userId) {
        authRepo.deleteByWorkflowKeyAndUserId(normalizeKey(workflowKey), userId);
        return ResponseEntity.ok(Map.of("message", "User authorization revoked"));
    }

    /** DELETE /api/assignments/workflow/{workflowKey}/authorizations/group/{groupId} */
    @DeleteMapping("/workflow/{workflowKey}/authorizations/group/{groupId}")
    public ResponseEntity<?> revokeGroupAuthorization(@PathVariable String workflowKey,
                                                      @PathVariable String groupId) {
        authRepo.deleteByWorkflowKeyAndGroupId(normalizeKey(workflowKey), groupId);
        return ResponseEntity.ok(Map.of("message", "Group authorization revoked"));
    }

    // =========================================================================
    // SECTION 4: TASK ROUTING RULES
    // =========================================================================

    /**
     * GET /api/assignments/workflow/{workflowKey}/rules
     * Returns all task routing rules for a given workflow.
     */
    @GetMapping("/workflow/{workflowKey}/rules")
    public ResponseEntity<?> getRoutingRules(@PathVariable String workflowKey) {
        String key = normalizeKey(workflowKey);
        List<TaskRoutingRule> rules = routingRuleRepo.findByWorkflowKey(key);
        Map<String, String> groupNames = fetchGroupNamesMap();

        List<Map<String, Object>> result = rules.stream()
                .map(r -> routingRuleToMap(r, groupNames))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/assignments/workflow/{workflowKey}/tasks/{taskId}/rules
     * Returns the routing rule for a specific task, if one exists.
     */
    @GetMapping("/workflow/{workflowKey}/tasks/{taskId}/rules")
    public ResponseEntity<?> getRoutingRuleForTask(@PathVariable String workflowKey,
                                                    @PathVariable String taskId) {
        Optional<TaskRoutingRule> ruleOpt = routingRuleRepo.findByWorkflowKeyAndTaskId(
                normalizeKey(workflowKey), taskId);

        if (ruleOpt.isPresent()) {
            Map<String, String> groupNames = fetchGroupNamesMap();
            return ResponseEntity.ok(routingRuleToMap(ruleOpt.get(), groupNames));
        }
        return ResponseEntity.ok(Collections.emptyMap());
    }

    /**
     * POST /api/assignments/workflow/{workflowKey}/tasks/{taskId}/rules
     * Creates or updates the routing rule for a specific task.
     *
     * Body:
     * {
     *   "routingType": "STAR" | "DYNAMIC_USER" | "SELECT_USER" | "SELECT_GROUP",
     *   "targetUserId":  "uuid"           (required when routingType=SELECT_USER),
     *   "targetGroupId": "group-id-str"   (required when routingType=SELECT_GROUP)
     * }
     */
    @PostMapping("/workflow/{workflowKey}/tasks/{taskId}/rules")
    public ResponseEntity<?> saveRoutingRule(@PathVariable String workflowKey,
                                              @PathVariable String taskId,
                                              @RequestBody Map<String, Object> body) {
        String typeStr = (String) body.get("routingType");
        if (typeStr == null || typeStr.isBlank()) {
            typeStr = (String) body.get("assignmentType");
        }
        if (typeStr == null || typeStr.isBlank()) return badRequest("'routingType' is required");

        if ("SELECT".equalsIgnoreCase(typeStr)) {
            typeStr = "SELECT_USER";
        }

        TaskRoutingRule.RoutingType routingType;
        try {
            routingType = TaskRoutingRule.RoutingType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid routingType. Allowed: STAR, DYNAMIC_USER, SELECT_USER, SELECT_GROUP");
        }

        String key = normalizeKey(workflowKey);

        // Upsert: find existing rule or create new
        TaskRoutingRule rule = routingRuleRepo.findByWorkflowKeyAndTaskId(key, taskId)
                .orElse(new TaskRoutingRule());

        rule.setWorkflowKey(key);
        rule.setTaskId(taskId);
        rule.setRoutingType(routingType);
        rule.setTargetUserId(null);
        rule.setTargetGroupId(null);

        // Validate and populate type-specific fields
        switch (routingType) {
            case SELECT_USER -> {
                String uid = (String) body.get("targetUserId");
                if (uid == null || uid.isBlank()) {
                    uid = (String) body.get("assigneeUserId");
                }
                if (uid == null || uid.isBlank()) return badRequest("'targetUserId' is required for SELECT_USER");
                rule.setTargetUserId(uid);
            }
            case SELECT_GROUP -> {
                Object gIdObj = body.get("targetGroupId");
                if (gIdObj == null || gIdObj.toString().isBlank()) {
                    gIdObj = body.get("assigneeGroupId");
                }
                if (gIdObj == null || gIdObj.toString().isBlank()) {
                    gIdObj = body.get("groupId");
                }
                if (gIdObj == null || gIdObj.toString().isBlank()) return badRequest("'targetGroupId' is required for SELECT_GROUP");
                rule.setTargetGroupId(gIdObj.toString().trim());
            }
            default -> {}
        }

        TaskRoutingRule saved = routingRuleRepo.save(rule);

        // Immediately update active running task instances in Camunda
        try {
            taskInterceptorService.applyRuleToActiveTasks(key, taskId, saved);
        } catch (Exception e) {
            log.warn("[ROUTING] Could not apply rule to active tasks: {}", e.getMessage());
        }

        Map<String, String> groupNames = fetchGroupNamesMap();
        return ResponseEntity.ok(Map.of(
                "message", "Routing rule saved",
                "rule",    routingRuleToMap(saved, groupNames)
        ));
    }

    /**
     * DELETE /api/assignments/workflow/{workflowKey}/tasks/{taskId}/rules
     * Removes the routing rule for a specific task.
     */
    @DeleteMapping("/workflow/{workflowKey}/tasks/{taskId}/rules")
    public ResponseEntity<?> deleteRoutingRule(@PathVariable String workflowKey,
                                                @PathVariable String taskId) {
        routingRuleRepo.deleteByWorkflowKeyAndTaskId(normalizeKey(workflowKey), taskId);
        return ResponseEntity.ok(Map.of("message", "Routing rule deleted"));
    }

    // =========================================================================
    // SECTION 5: USER LIST HELPER (for assignment dropdowns)
    // =========================================================================

    /** GET /api/assignments/users — Returns all active users (for UI dropdowns) */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepo.findByIsActiveTrue().stream()
                .map(this::userToMap)
                .collect(Collectors.toList()));
    }

    // =========================================================================
    // RESPONSE BUILDERS (prevent direct entity serialization)
    // =========================================================================

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",       u.getId());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email",    u.getEmail());
        m.put("isActive", u.isActive());
        return m;
    }

    private Map<String, Object> routingRuleToMap(TaskRoutingRule r, Map<String, String> groupNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          r.getId());
        m.put("workflowKey", r.getWorkflowKey());
        m.put("taskId",      r.getTaskId());
        m.put("routingType", r.getRoutingType() != null ? r.getRoutingType().name() : null);

        if (r.getRoutingType() == TaskRoutingRule.RoutingType.SELECT_USER && r.getTargetUserId() != null) {
            String uid = r.getTargetUserId();
            User u = userRepo.findById(uid).or(() -> userRepo.findByUsername(uid)).orElse(null);
            m.put("targetUserId",       uid);
            m.put("targetUsername",     u != null ? u.getUsername() : uid);
            m.put("targetUserFullName", u != null ? u.getFullName()  : uid);
        }

        if (r.getRoutingType() == TaskRoutingRule.RoutingType.SELECT_GROUP && r.getTargetGroupId() != null) {
            String gId = r.getTargetGroupId();
            m.put("targetGroupId",   gId);
            m.put("targetGroupName", groupNames.getOrDefault(gId, gId));
        }

        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        return m;
    }
}
