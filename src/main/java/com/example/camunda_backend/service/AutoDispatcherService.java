package com.example.camunda_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class AutoDispatcherService {

    @Value("${camunda.api.url:http://localhost:8080/engine-rest}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    
    // 1. Inject our new TaskSyncService
    private final TaskSyncService taskSyncService;

    public AutoDispatcherService(TaskSyncService taskSyncService) {
        this.taskSyncService = taskSyncService;
    }

    @Scheduled(fixedDelay = 5000)
    public void roundRobinAutoDispatch() {
        try {
            String unassignedUrl = camundaUrl + "/task?unassigned=true";
            List<Map<String, Object>> unassignedTasks = restTemplate.getForObject(unassignedUrl, List.class);

            if (unassignedTasks == null || unassignedTasks.isEmpty()) return;

            for (Map<String, Object> task : unassignedTasks) {
                String taskId = (String) task.get("id");
                String taskName = (String) task.get("name");
                String processId = (String) task.get("processInstanceId");
                String taskDefKey = (String) task.get("taskDefinitionKey");

                String identityLinksUrl = camundaUrl + "/task/" + taskId + "/identity-links?type=candidate";
                List<Map<String, Object>> links = restTemplate.getForObject(identityLinksUrl, List.class);
                if (links == null || links.isEmpty()) continue;

                String targetGroup = (String) links.get(0).get("groupId");
                if (targetGroup == null) continue;

                String usersUrl = camundaUrl + "/user?memberOfGroup=" + targetGroup;
                List<Map<String, Object>> groupMembers = restTemplate.getForObject(usersUrl, List.class);
                if (groupMembers == null || groupMembers.isEmpty()) continue;

                String leastBurdenedUserId = null;
                long lowestTaskCount = Long.MAX_VALUE;

                for (Map<String, Object> user : groupMembers) {
                    String userId = (String) user.get("id");
                    String countUrl = camundaUrl + "/task/count?assignee=" + userId + "&active=true";
                    Map<String, Object> countRes = restTemplate.getForObject(countUrl, Map.class);
                    long taskCount = ((Number) countRes.get("count")).longValue();

                    if (taskCount < lowestTaskCount) {
                        lowestTaskCount = taskCount;
                        leastBurdenedUserId = userId;
                    }
                }

                if (leastBurdenedUserId != null) {
                    String assignUrl = camundaUrl + "/task/" + taskId + "/assignee";
                    restTemplate.postForLocation(assignUrl, Map.of("userId", leastBurdenedUserId));
                    System.out.println("🤖 [AUTO-DISPATCH] Task '" + taskName + "' assigned to: -> " + leastBurdenedUserId + " <-");

                    // 2. INSTANT MYSQL SYNC: Update MySQL the millisecond the engine assigns the task!
                    taskSyncService.syncTask(taskId, processId, taskName, taskDefKey, leastBurdenedUserId, targetGroup, "ASSIGNED");
                }
            }
        } catch (Exception e) {
            System.err.println("Auto-Dispatcher Error: " + e.getMessage());
        }
    }
}