package com.example.camunda_backend.service;

import com.example.camunda_backend.entity.AppGroup;
import com.example.camunda_backend.entity.TaskRoutingRule;
import com.example.camunda_backend.entity.User;
import com.example.camunda_backend.entity.UserGroupMapping;
import com.example.camunda_backend.entity.WorkflowAuthorization;
import com.example.camunda_backend.repository.AppGroupRepository;
import com.example.camunda_backend.repository.TaskRoutingRuleRepository;
import com.example.camunda_backend.repository.UserGroupMappingRepository;
import com.example.camunda_backend.repository.UserRepository;
import com.example.camunda_backend.repository.WorkflowAuthorizationRepository;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * =========================================================================
 * TASK INTERCEPTOR SERVICE — Core Assignment & Routing Engine
 * =========================================================================
 *
 * Implements automated task assignment and routing for workflows in Camunda.
 * Runs in the background every 2.5 seconds AND can be triggered on demand
 * (e.g. upon process start or manual assignment).
 *
 * Routing Strategies:
 *   STAR         → Assigns to the workflow initiator (reads 'starterUserId' or 'initiator')
 *   DYNAMIC_USER → Load-balances across authorized users based on lowest active task count
 *   SELECT_USER  → Statically assigns to the specific configured user
 *   SELECT_GROUP → Sets the task's candidateGroup so any group member can claim it
 *
 * Fallback: If no task-level rule exists, applies workflow-level authorizations
 * as candidate groups and candidate users on the task.
 * =========================================================================
 */
@Service
public class TaskInterceptorService implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(TaskInterceptorService.class);

    @Value("${camunda.api.url}")
    private String camundaApiUrl;

    private final TaskRoutingRuleRepository routingRuleRepo;
    private final WorkflowAuthorizationRepository authRepo;
    private final UserRepository userRepository;
    private final UserGroupMappingRepository userGroupMappingRepo;
    private final AppGroupRepository appGroupRepo;
    private final RestTemplate restTemplate = new RestTemplate();

    public TaskInterceptorService(TaskRoutingRuleRepository routingRuleRepo,
                                  WorkflowAuthorizationRepository authRepo,
                                  UserRepository userRepository,
                                  UserGroupMappingRepository userGroupMappingRepo,
                                  AppGroupRepository appGroupRepo) {
        this.routingRuleRepo       = routingRuleRepo;
        this.authRepo              = authRepo;
        this.userRepository        = userRepository;
        this.userGroupMappingRepo  = userGroupMappingRepo;
        this.appGroupRepo          = appGroupRepo;
    }

    // =========================================================================
    // BACKGROUND SCHEDULER & ON-DEMAND DISPATCHER
    // =========================================================================

    /**
     * Polls Camunda for unassigned tasks every 2.5 seconds and applies rules.
     */
    @Scheduled(fixedDelay = 2500)
    public void scheduledDispatch() {
        dispatchUnassignedTasks();
    }

    /**
     * Public method to run assignment immediately (e.g. after starting a process).
     */
    public synchronized void dispatchUnassignedTasks() {
        try {
            String unassignedUrl = camundaApiUrl + "/task?unassigned=true";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    unassignedUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> tasks = response.getBody();
            if (tasks == null || tasks.isEmpty()) return;

            for (Map<String, Object> task : tasks) {
                routeSingleTask(task);
            }
        } catch (Exception e) {
            log.debug("[DISPATCH] Could not query unassigned tasks: {}", e.getMessage());
        }
    }

    private void routeSingleTask(Map<String, Object> task) {
        String taskId       = (String) task.get("id");
        String taskName     = (String) task.get("name");
        String procDefId    = (String) task.get("processDefinitionId");
        String procInstId   = (String) task.get("processInstanceId");
        String taskDefKey   = (String) task.get("taskDefinitionKey");

        if (taskId == null || procDefId == null || taskDefKey == null) return;

        String workflowKey = extractWorkflowKey(procDefId);

        // Skip if task already has candidate links (already routed to candidateGroup or candidate users)
        if (hasCandidateLinks(taskId)) {
            return;
        }

        // 1. Task-level routing rule has highest priority
        Optional<TaskRoutingRule> ruleOpt = routingRuleRepo.findByWorkflowKeyAndTaskId(workflowKey, taskDefKey);
        if (ruleOpt.isPresent()) {
            TaskRoutingRule rule = ruleOpt.get();
            applyRoutingRule(taskId, taskDefKey, taskName, procInstId, workflowKey, rule);
            return;
        }

        // 2. Fallback: Apply workflow-level authorizations
        applyWorkflowLevelAuthorizations(taskId, taskDefKey, workflowKey);
    }

    public void applyRoutingRule(String taskId, String taskDefKey, String taskName,
                                 String procInstId, String workflowKey, TaskRoutingRule rule) {
        switch (rule.getRoutingType()) {
            case SELECT_USER -> {
                String targetUserId = rule.getTargetUserId();
                String username = resolveUsernameFromId(targetUserId);
                if (username != null) {
                    clearCandidateLinks(taskId);
                    assignTaskAssignee(taskId, username);
                    log.info("[ASSIGNMENT] Task '{}' ({}) assigned to user '{}' (SELECT_USER)",
                            taskDefKey, taskId, username);
                } else {
                    log.warn("[SELECT_USER] Could not resolve username for userId '{}'", targetUserId);
                }
            }

            case SELECT_GROUP -> {
                String targetGroupId = rule.getTargetGroupId();
                if (targetGroupId != null && !targetGroupId.isBlank()) {
                    try {
                        restTemplate.postForLocation(camundaApiUrl + "/task/" + taskId + "/assignee",
                                Collections.singletonMap("userId", null));
                    } catch (Exception ignored) {}
                    clearCandidateLinks(taskId);
                    addCandidateGroup(taskId, targetGroupId);
                    log.info("[ASSIGNMENT] Task '{}' ({}) placed in candidateGroup '{}' (SELECT_GROUP)",
                            taskDefKey, taskId, targetGroupId);
                } else {
                    log.warn("[SELECT_GROUP] Rule for task '{}' has no targetGroupId", taskDefKey);
                }
            }

            case STAR -> {
                String initiator = resolveProcessInitiator(procInstId);
                if (initiator != null && !initiator.isBlank()) {
                    clearCandidateLinks(taskId);
                    assignTaskAssignee(taskId, initiator);
                    log.info("[ASSIGNMENT] Task '{}' ({}) assigned to initiator '{}' (STAR)",
                            taskDefKey, taskId, initiator);
                } else {
                    log.warn("[STAR] Could not resolve starter for process '{}'", procInstId);
                }
            }

            case DYNAMIC_USER -> {
                String leastBurdenedUser = findLeastBurdenedUser(workflowKey);
                if (leastBurdenedUser != null) {
                    clearCandidateLinks(taskId);
                    assignTaskAssignee(taskId, leastBurdenedUser);
                    log.info("[ASSIGNMENT] Task '{}' ({}) assigned to least burdened user '{}' (DYNAMIC_USER)",
                            taskDefKey, taskId, leastBurdenedUser);
                } else {
                    log.warn("[DYNAMIC_USER] No eligible users to load balance for workflow '{}'", workflowKey);
                }
            }
        }
    }

    /**
     * Finds active tasks matching workflowKey and taskDefKey and applies the rule immediately.
     */
    public void applyRuleToActiveTasks(String workflowKey, String taskDefKey, TaskRoutingRule rule) {
        try {
            String url = camundaApiUrl + "/task?processDefinitionKey=" + workflowKey + "&taskDefinitionKey=" + taskDefKey;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            List<Map<String, Object>> tasks = response.getBody();
            if (tasks != null && !tasks.isEmpty()) {
                for (Map<String, Object> task : tasks) {
                    String taskId = (String) task.get("id");
                    String taskName = (String) task.get("name");
                    String procInstId = (String) task.get("processInstanceId");
                    clearCandidateLinks(taskId);
                    try {
                        restTemplate.postForLocation(camundaApiUrl + "/task/" + taskId + "/assignee",
                                Collections.singletonMap("userId", null));
                    } catch (Exception ignored) {}
                    applyRoutingRule(taskId, taskDefKey, taskName, procInstId, workflowKey, rule);
                }
                log.info("[ROUTING] Updated {} active tasks for {}:{}", tasks.size(), workflowKey, taskDefKey);
            }
        } catch (Exception e) {
            log.error("[ROUTING] Failed to apply rule to active tasks for {}:{}: {}", workflowKey, taskDefKey, e.getMessage());
        }
    }

    private void applyWorkflowLevelAuthorizations(String taskId, String taskDefKey, String workflowKey) {
        List<WorkflowAuthorization> auths = authRepo.findByWorkflowKey(workflowKey);
        if (auths.isEmpty()) return;

        boolean applied = false;
        for (WorkflowAuthorization auth : auths) {
            if (auth.isGroupAuthorization()) {
                addCandidateGroup(taskId, auth.getGroupId());
                applied = true;
            } else if (auth.isUserAuthorization()) {
                String username = resolveUsernameFromId(auth.getUserId());
                if (username != null) {
                    addCandidateUser(taskId, username);
                    applied = true;
                }
            }
        }

        if (applied) {
            log.info("[ASSIGNMENT] Task '{}' ({}) assigned workflow-level authorization candidate links",
                    taskDefKey, taskId);
        }
    }

    // =========================================================================
    // MANUAL ASSIGNMENT & ENRICHMENT HELPERS
    // =========================================================================

    public void assignTaskToUser(String taskId, String userIdOrUsername) {
        String username = resolveUsernameFromId(userIdOrUsername);
        if (username == null || username.isBlank()) {
            username = userIdOrUsername;
        }
        clearCandidateLinks(taskId);
        assignTaskAssignee(taskId, username);
        log.info("[ASSIGNMENT] Task '{}' manually assigned to user '{}'", taskId, username);
    }

    public void assignTaskToGroup(String taskId, String groupId) {
        if (groupId == null || groupId.isBlank()) return;
        try {
            restTemplate.postForLocation(camundaApiUrl + "/task/" + taskId + "/assignee",
                    Collections.singletonMap("userId", null));
        } catch (Exception ignored) {}
        clearCandidateLinks(taskId);
        addCandidateGroup(taskId, groupId);
        log.info("[ASSIGNMENT] Task '{}' manually assigned to candidateGroup '{}'", taskId, groupId);
    }

    public void claimTask(String taskId, String username) {
        if (taskId == null || username == null) return;
        try {
            // Camunda's native /task/{id}/claim sets assignee while preserving candidateGroup identity links
            String url = camundaApiUrl + "/task/" + taskId + "/claim";
            restTemplate.postForLocation(url, Collections.singletonMap("userId", username));
            log.info("[CLAIM] Task '{}' claimed by user '{}'", taskId, username);
        } catch (Exception e) {
            log.warn("[CLAIM] Camunda /claim call failed for task '{}': {}, falling back to direct assignee", taskId, e.getMessage());
            assignTaskAssignee(taskId, username);
        }
    }

    public void unclaimTask(String taskId, String fallbackGroupId) {
        if (taskId == null) return;
        try {
            // Camunda's native /task/{id}/unclaim sets assignee to null while keeping candidate links intact
            String url = camundaApiUrl + "/task/" + taskId + "/unclaim";
            restTemplate.postForLocation(url, Collections.emptyMap());
            log.info("[UNCLAIM] Task '{}' unclaimed via Camunda API", taskId);
        } catch (Exception e) {
            log.warn("[UNCLAIM] Camunda /unclaim call failed for task '{}': {}, resetting assignee", taskId, e.getMessage());
            try {
                restTemplate.postForLocation(camundaApiUrl + "/task/" + taskId + "/assignee", Collections.singletonMap("userId", null));
            } catch (Exception ignored) {}
        }

        // Ensure candidate links exist so task returns to group pool
        if (!hasCandidateLinks(taskId)) {
            String targetGroup = fallbackGroupId;
            if (targetGroup == null || targetGroup.isBlank()) {
                try {
                    Map<String, Object> task = restTemplate.getForObject(camundaApiUrl + "/task/" + taskId, Map.class);
                    if (task != null) {
                        String procDefId = (String) task.get("processDefinitionId");
                        String taskDefKey = (String) task.get("taskDefinitionKey");
                        String workflowKey = extractWorkflowKey(procDefId);
                        Optional<TaskRoutingRule> ruleOpt = routingRuleRepo.findByWorkflowKeyAndTaskId(workflowKey, taskDefKey);
                        if (ruleOpt.isPresent() && ruleOpt.get().getRoutingType() == TaskRoutingRule.RoutingType.SELECT_GROUP) {
                            targetGroup = ruleOpt.get().getTargetGroupId();
                        } else {
                            List<WorkflowAuthorization> auths = authRepo.findByWorkflowKey(workflowKey);
                            for (WorkflowAuthorization a : auths) {
                                if (a.isGroupAuthorization()) {
                                    targetGroup = a.getGroupId();
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (targetGroup != null && !targetGroup.isBlank()) {
                addCandidateGroup(taskId, targetGroup);
                log.info("[UNCLAIM] Restored candidateGroup '{}' on unclaimed task '{}'", targetGroup, taskId);
            }
        }
    }

    public void unassignTask(String taskId) {
        try {
            restTemplate.postForLocation(camundaApiUrl + "/task/" + taskId + "/assignee",
                    Collections.singletonMap("userId", null));
        } catch (Exception ignored) {}
        clearCandidateLinks(taskId);
        log.info("[ASSIGNMENT] Task '{}' unassigned", taskId);
    }

    public void clearCandidateLinks(String taskId) {
        try {
            String url = camundaApiUrl + "/task/" + taskId + "/identity-links";
            List<Map<String, Object>> links = restTemplate.getForObject(url, List.class);
            if (links != null) {
                for (Map<String, Object> link : links) {
                    if ("candidate".equals(link.get("type"))) {
                        try {
                            String delUrl = camundaApiUrl + "/task/" + taskId + "/identity-links/delete";
                            restTemplate.postForLocation(delUrl, link);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ASSIGNMENT] Failed to clear candidate links on task {}: {}", taskId, e.getMessage());
        }
    }

    public Map<String, String> fetchGroupNamesMap() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String url = camundaApiUrl + "/group";
            List<Map<String, Object>> groups = restTemplate.getForObject(url, List.class);
            if (groups != null) {
                for (Map<String, Object> g : groups) {
                    String id = (String) g.get("id");
                    String name = (String) g.get("name");
                    if (id != null) map.put(id, (name != null && !name.isBlank()) ? name : id);
                }
            }
        } catch (Exception e) {
            log.warn("[GROUPS] Failed to fetch group map: {}", e.getMessage());
        }
        return map;
    }

    public Map<String, String> fetchUserNamesMap() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            List<User> users = userRepository.findAll();
            for (User u : users) {
                if (u.getUsername() != null) {
                    map.put(u.getUsername(), u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername());
                }
                if (u.getId() != null) {
                    map.put(u.getId(), u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername());
                }
            }
        } catch (Exception e) {
            log.warn("[USERS] Failed to fetch user map: {}", e.getMessage());
        }
        return map;
    }

    public List<Map<String, Object>> enrichTasks(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.isEmpty()) return Collections.emptyList();

        Map<String, String> groupNames = fetchGroupNamesMap();
        Map<String, String> userNames = fetchUserNamesMap();

        for (Map<String, Object> task : tasks) {
            String taskId = (String) task.get("id");
            String assignee = (String) task.get("assignee");
            if (assignee != null && !assignee.isBlank()) {
                task.put("assigneeName", userNames.getOrDefault(assignee, assignee));
            }

            if (taskId != null && task.get("endTime") == null) {
                try {
                    String url = camundaApiUrl + "/task/" + taskId + "/identity-links";
                    List<Map<String, Object>> links = restTemplate.getForObject(url, List.class);
                    if (links != null) {
                        List<String> candidateGroups = new ArrayList<>();
                        List<String> candidateGroupNames = new ArrayList<>();
                        List<String> candidateUsers = new ArrayList<>();

                        for (Map<String, Object> link : links) {
                            if ("candidate".equals(link.get("type"))) {
                                String gId = (String) link.get("groupId");
                                String uId = (String) link.get("userId");
                                if (gId != null && !gId.isBlank()) {
                                    candidateGroups.add(gId);
                                    candidateGroupNames.add(groupNames.getOrDefault(gId, gId));
                                }
                                if (uId != null && !uId.isBlank()) {
                                    candidateUsers.add(uId);
                                }
                            }
                        }

                        if (!candidateGroups.isEmpty()) {
                            task.put("candidateGroup", String.join(", ", candidateGroups));
                            task.put("candidateGroupName", String.join(", ", candidateGroupNames));
                            task.put("candidateGroups", candidateGroups);
                        }
                        if (!candidateUsers.isEmpty()) {
                            task.put("candidateUsers", candidateUsers);
                        }
                    }
                } catch (Exception ignored) {}

                if (task.get("candidateGroup") == null) {
                    try {
                        String procDefId = (String) task.get("processDefinitionId");
                        String taskDefKey = (String) task.get("taskDefinitionKey");
                        if (procDefId != null && taskDefKey != null) {
                            String workflowKey = extractWorkflowKey(procDefId);
                            Optional<TaskRoutingRule> ruleOpt = routingRuleRepo.findByWorkflowKeyAndTaskId(workflowKey, taskDefKey);
                            if (ruleOpt.isPresent() && ruleOpt.get().getRoutingType() == TaskRoutingRule.RoutingType.SELECT_GROUP) {
                                String gId = ruleOpt.get().getTargetGroupId();
                                if (gId != null && !gId.isBlank()) {
                                    task.put("candidateGroup", gId);
                                    task.put("candidateGroupName", groupNames.getOrDefault(gId, gId));
                                    task.put("candidateGroups", List.of(gId));
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Task duration calculation
                Number taskDur = (Number) task.get("durationInMillis");
                if (taskDur != null) {
                    task.put("duration", taskDur.longValue());
                } else {
                    String taskStartStr = (String) task.get("startTime");
                    if (taskStartStr != null) {
                        try {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                            long startMs = sdf.parse(taskStartStr).getTime();
                            task.put("duration", Math.max(0, System.currentTimeMillis() - startMs));
                        } catch (Exception ex) {
                            task.put("duration", null);
                        }
                    }
                }
            }
        }
        return tasks;
    }

    // =========================================================================
    // USER TASKS FILTERING & GROUP MEMBERSHIP HELPERS
    // =========================================================================

    /**
     * Retrieves tasks assigned to the specified user OR to any candidate group the user belongs to.
     * Guaranteed to NOT return tasks assigned to other users or unrelated groups.
     */
    public List<Map<String, Object>> getTasksForUser(String userIdOrUsername) {
        if (userIdOrUsername == null || userIdOrUsername.isBlank()) {
            return Collections.emptyList();
        }

        String username = resolveUsernameFromId(userIdOrUsername);
        if (username == null || username.isBlank()) {
            username = userIdOrUsername;
        }

        Set<String> userGroupIds = getUserGroupIds(username);
        Map<String, Map<String, Object>> taskMap = new LinkedHashMap<>();

        // 1. Direct assignee tasks: GET /task?assignee={username}&sortBy=created&sortOrder=desc
        try {
            String url = camundaApiUrl + "/task?assignee=" + username + "&sortBy=created&sortOrder=desc";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            List<Map<String, Object>> assigned = response.getBody();
            if (assigned != null) {
                for (Map<String, Object> t : assigned) {
                    if (t.get("id") != null) {
                        taskMap.put((String) t.get("id"), t);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MY-TASKS] Failed to query assignee tasks for '{}': {}", username, e.getMessage());
        }

        // 2. Candidate user tasks from Camunda: GET /task?candidateUser={username}&sortBy=created&sortOrder=desc
        try {
            String url = camundaApiUrl + "/task?candidateUser=" + username + "&sortBy=created&sortOrder=desc";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            List<Map<String, Object>> candidateTasks = response.getBody();
            if (candidateTasks != null) {
                for (Map<String, Object> t : candidateTasks) {
                    if (t.get("id") != null) {
                        String taskAssignee = (String) t.get("assignee");
                        if (taskAssignee == null || taskAssignee.isBlank() || taskAssignee.equals(username)) {
                            taskMap.put((String) t.get("id"), t);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MY-TASKS] Failed to query candidateUser tasks for '{}': {}", username, e.getMessage());
        }

        // 3. Candidate tasks for each group this user belongs to
        for (String gId : userGroupIds) {
            try {
                String url = camundaApiUrl + "/task?candidateGroup=" + gId + "&sortBy=created&sortOrder=desc";
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {}
                );
                List<Map<String, Object>> groupTasks = response.getBody();
                if (groupTasks != null) {
                    for (Map<String, Object> t : groupTasks) {
                        if (t.get("id") != null) {
                            String taskAssignee = (String) t.get("assignee");
                            // Only include if in group pool (unassigned) or assigned to this user
                            if (taskAssignee == null || taskAssignee.isBlank() || taskAssignee.equals(username)) {
                                taskMap.put((String) t.get("id"), t);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[MY-TASKS] Failed to query group tasks for group '{}': {}", gId, e.getMessage());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(taskMap.values());
        return enrichTasks(result);
    }

    public Set<String> getUserGroupIds(String username) {
        Set<String> groupIds = new LinkedHashSet<>();
        if (username == null || username.isBlank()) return groupIds;

        // 1. From Camunda native group membership: GET /group?member={username}
        try {
            String url = camundaApiUrl + "/group?member=" + username;
            List<Map<String, Object>> groups = restTemplate.getForObject(url, List.class);
            if (groups != null) {
                for (Map<String, Object> g : groups) {
                    String id = (String) g.get("id");
                    if (id != null && !id.isBlank()) {
                        groupIds.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GROUPS] Could not query Camunda groups for user '{}': {}", username, e.getMessage());
        }

        // 2. From MySQL user_group_mapping
        try {
            userRepository.findByUsername(username).or(() -> userRepository.findById(username)).ifPresent(u -> {
                List<UserGroupMapping> mappings = userGroupMappingRepo.findByUserId(u.getId());
                if (mappings != null) {
                    for (UserGroupMapping m : mappings) {
                        if (m.getGroup() != null && m.getGroup().getName() != null) {
                            groupIds.add(m.getGroup().getName());
                        }
                    }
                }
            });
        } catch (Exception ignored) {}

        return groupIds;
    }

    public void syncUserToCamunda(User user) {
        if (user == null || user.getUsername() == null) return;
        try {
            String checkUrl = camundaApiUrl + "/user/" + user.getUsername();
            try {
                restTemplate.getForObject(checkUrl, Object.class);
                return; // User already exists in Camunda
            } catch (Exception notFound) {
                String createUrl = camundaApiUrl + "/user/create";
                String firstName = user.getFullName() != null ? user.getFullName().split(" ")[0] : user.getUsername();
                String lastName = user.getFullName() != null && user.getFullName().contains(" ")
                        ? user.getFullName().substring(user.getFullName().indexOf(" ") + 1) : "";

                Map<String, Object> profile = new HashMap<>();
                profile.put("id", user.getUsername());
                profile.put("firstName", firstName);
                profile.put("lastName", lastName);
                profile.put("email", user.getEmail() != null ? user.getEmail() : "");

                Map<String, Object> credentials = Map.of("password", "Default@1234");
                Map<String, Object> body = Map.of("profile", profile, "credentials", credentials);

                restTemplate.postForLocation(createUrl, body);
                log.info("[SYNC] Synced user '{}' to Camunda identity", user.getUsername());
            }
        } catch (Exception e) {
            log.warn("[SYNC] Could not sync user '{}' to Camunda: {}", user.getUsername(), e.getMessage());
        }
    }

    public void addUserToGroup(String groupId, String userIdOrUsername) {
        if (groupId == null || groupId.isBlank() || userIdOrUsername == null || userIdOrUsername.isBlank()) return;

        User user = userRepository.findByUsername(userIdOrUsername)
                .or(() -> userRepository.findById(userIdOrUsername))
                .orElse(null);

        String username = user != null ? user.getUsername() : userIdOrUsername;

        if (user != null) {
            syncUserToCamunda(user);
        }

        // 1. Add to Camunda group
        try {
            String url = camundaApiUrl + "/group/" + groupId + "/members/" + username;
            restTemplate.put(url, null);
            log.info("[GROUPS] Added user '{}' to Camunda group '{}'", username, groupId);
        } catch (Exception e) {
            log.warn("[GROUPS] Could not add user '{}' to Camunda group '{}': {}", username, groupId, e.getMessage());
        }

        // 2. Add to MySQL user_group_mapping
        if (user != null) {
            try {
                AppGroup group = appGroupRepo.findByName(groupId)
                        .orElseGet(() -> appGroupRepo.save(new AppGroup(groupId, groupId)));

                if (!userGroupMappingRepo.existsByUserIdAndGroup_Id(user.getId(), group.getId())) {
                    userGroupMappingRepo.save(new UserGroupMapping(user.getId(), group));
                }
            } catch (Exception e) {
                log.warn("[GROUPS] Could not save user_group_mapping for user '{}' in group '{}': {}",
                        username, groupId, e.getMessage());
            }
        }
    }

    public void removeUserFromGroup(String groupId, String userIdOrUsername) {
        if (groupId == null || groupId.isBlank() || userIdOrUsername == null || userIdOrUsername.isBlank()) return;

        User user = userRepository.findByUsername(userIdOrUsername)
                .or(() -> userRepository.findById(userIdOrUsername))
                .orElse(null);

        String username = user != null ? user.getUsername() : userIdOrUsername;

        // 1. Remove from Camunda group
        try {
            String url = camundaApiUrl + "/group/" + groupId + "/members/" + username;
            restTemplate.delete(url);
            log.info("[GROUPS] Removed user '{}' from Camunda group '{}'", username, groupId);
        } catch (Exception e) {
            log.warn("[GROUPS] Could not remove user '{}' from Camunda group '{}': {}", username, groupId, e.getMessage());
        }

        // 2. Remove from MySQL user_group_mapping
        if (user != null) {
            try {
                appGroupRepo.findByName(groupId).ifPresent(g -> {
                    userGroupMappingRepo.deleteByUserIdAndGroupId(user.getId(), g.getId());
                });
            } catch (Exception e) {
                log.warn("[GROUPS] Could not delete user_group_mapping for user '{}' in group '{}': {}",
                        username, groupId, e.getMessage());
            }
        }
    }

    public List<Map<String, Object>> getGroupMembers(String groupId) {
        if (groupId == null || groupId.isBlank()) return Collections.emptyList();

        Map<String, Map<String, Object>> membersMap = new LinkedHashMap<>();

        // 1. Query Camunda group members
        try {
            String url = camundaApiUrl + "/user?memberOfGroup=" + groupId;
            List<Map<String, Object>> camundaUsers = restTemplate.getForObject(url, List.class);
            if (camundaUsers != null) {
                for (Map<String, Object> cu : camundaUsers) {
                    String uId = (String) cu.get("id");
                    if (uId != null && !uId.isBlank()) {
                        User dbUser = userRepository.findByUsername(uId).or(() -> userRepository.findById(uId)).orElse(null);
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", dbUser != null ? dbUser.getId() : uId);
                        m.put("username", uId);
                        m.put("fullName", dbUser != null && dbUser.getFullName() != null ? dbUser.getFullName() : uId);
                        m.put("email", dbUser != null && dbUser.getEmail() != null ? dbUser.getEmail() : (String) cu.get("email"));
                        membersMap.put(uId, m);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GROUPS] Failed to query Camunda group members for '{}': {}", groupId, e.getMessage());
        }

        // 2. Query MySQL user_group_mapping
        try {
            appGroupRepo.findByName(groupId).ifPresent(g -> {
                List<UserGroupMapping> mappings = userGroupMappingRepo.findByGroup_Id(g.getId());
                if (mappings != null) {
                    for (UserGroupMapping map : mappings) {
                        userRepository.findById(map.getUserId()).ifPresent(u -> {
                            if (!membersMap.containsKey(u.getUsername())) {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", u.getId());
                                m.put("username", u.getUsername());
                                m.put("fullName", u.getFullName());
                                m.put("email", u.getEmail());
                                membersMap.put(u.getUsername(), m);
                            }
                        });
                    }
                }
            });
        } catch (Exception ignored) {}

        return new ArrayList<>(membersMap.values());
    }

    public List<Map<String, Object>> getUserGroups(String userIdOrUsername) {
        if (userIdOrUsername == null || userIdOrUsername.isBlank()) return Collections.emptyList();

        User user = userRepository.findByUsername(userIdOrUsername)
                .or(() -> userRepository.findById(userIdOrUsername))
                .orElse(null);

        String username = user != null ? user.getUsername() : userIdOrUsername;
        Set<String> groupIds = getUserGroupIds(username);
        Map<String, String> groupNames = fetchGroupNamesMap();

        List<Map<String, Object>> list = new ArrayList<>();
        for (String gId : groupIds) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", gId);
            m.put("name", groupNames.getOrDefault(gId, gId));
            list.add(m);
        }
        return list;
    }

    // =========================================================================
    // CAMUNDA REST INTERACTION HELPERS
    // =========================================================================

    private void assignTaskAssignee(String taskId, String username) {
        try {
            String url = camundaApiUrl + "/task/" + taskId + "/assignee";
            restTemplate.postForLocation(url, Map.of("userId", username));
        } catch (Exception e) {
            log.error("[ASSIGNMENT] Failed to set assignee '{}' on task '{}': {}",
                    username, taskId, e.getMessage());
        }
    }

    private void addCandidateGroup(String taskId, String groupId) {
        try {
            String url = camundaApiUrl + "/task/" + taskId + "/identity-links";
            restTemplate.postForLocation(url, Map.of("groupId", groupId, "type", "candidate"));
        } catch (Exception e) {
            log.error("[ASSIGNMENT] Failed to add candidateGroup '{}' on task '{}': {}",
                    groupId, taskId, e.getMessage());
        }
    }

    private void addCandidateUser(String taskId, String username) {
        try {
            String url = camundaApiUrl + "/task/" + taskId + "/identity-links";
            restTemplate.postForLocation(url, Map.of("userId", username, "type", "candidate"));
        } catch (Exception e) {
            log.error("[ASSIGNMENT] Failed to add candidateUser '{}' on task '{}': {}",
                    username, taskId, e.getMessage());
        }
    }

    private boolean hasCandidateLinks(String taskId) {
        try {
            String url = camundaApiUrl + "/task/" + taskId + "/identity-links";
            List<Map<String, Object>> links = restTemplate.getForObject(url, List.class);
            if (links != null) {
                for (Map<String, Object> link : links) {
                    if ("candidate".equals(link.get("type"))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String resolveProcessInitiator(String processInstanceId) {
        if (processInstanceId == null) return null;

        // 1. Try starterUserId variable
        try {
            String url = camundaApiUrl + "/process-instance/" + processInstanceId + "/variables/starterUserId";
            Map<?, ?> var = restTemplate.getForObject(url, Map.class);
            if (var != null && var.get("value") != null) {
                return var.get("value").toString();
            }
        } catch (Exception ignored) {}

        // 2. Try initiator variable
        try {
            String url = camundaApiUrl + "/process-instance/" + processInstanceId + "/variables/initiator";
            Map<?, ?> var = restTemplate.getForObject(url, Map.class);
            if (var != null && var.get("value") != null) {
                return var.get("value").toString();
            }
        } catch (Exception ignored) {}

        // 3. Fallback: query history
        try {
            String url = camundaApiUrl + "/history/process-instance/" + processInstanceId;
            Map<?, ?> hist = restTemplate.getForObject(url, Map.class);
            if (hist != null && hist.get("startUserId") != null) {
                return hist.get("startUserId").toString();
            }
        } catch (Exception ignored) {}

        return null;
    }

    private String findLeastBurdenedUser(String workflowKey) {
        List<String> userIds = authRepo.findAuthorizedUserIdsByWorkflowKey(workflowKey);
        List<String> usernames = new ArrayList<>();

        if (userIds != null && !userIds.isEmpty()) {
            for (String uid : userIds) {
                String u = resolveUsernameFromId(uid);
                if (u != null) usernames.add(u);
            }
        }

        if (usernames.isEmpty()) {
            usernames = userRepository.findByIsActiveTrue().stream()
                    .map(User::getUsername)
                    .collect(Collectors.toList());
        }

        if (usernames.isEmpty()) return null;

        String leastBurdenedUser = null;
        long lowestTaskCount = Long.MAX_VALUE;

        for (String u : usernames) {
            long count = getActiveTaskCountForUser(u);
            if (count < lowestTaskCount) {
                lowestTaskCount = count;
                leastBurdenedUser = u;
            }
        }

        return leastBurdenedUser;
    }

    private long getActiveTaskCountForUser(String username) {
        try {
            String url = camundaApiUrl + "/task/count?assignee=" + username + "&active=true";
            Map<?, ?> res = restTemplate.getForObject(url, Map.class);
            if (res != null && res.get("count") instanceof Number) {
                return ((Number) res.get("count")).longValue();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public String resolveUsernameFromId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElseGet(() -> userRepository.findByUsername(userId)
                        .map(User::getUsername)
                        .orElse(userId));
    }

    private String extractWorkflowKey(String processDefId) {
        if (processDefId == null) return "";
        return processDefId.contains(":") ? processDefId.split(":")[0] : processDefId;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        try {
            dispatchUnassignedTasks();
        } catch (Exception ignored) {}
    }
}
