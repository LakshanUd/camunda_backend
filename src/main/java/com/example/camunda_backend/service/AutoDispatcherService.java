package com.example.camunda_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class AutoDispatcherService {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Runs automatically in the background every 5 seconds (5000 milliseconds)
    @Scheduled(fixedDelay = 5000)
    public void roundRobinAutoDispatch() {
        try {
            // 1. Fetch all unassigned tasks sitting in departmental holding pools
            String unassignedUrl = camundaUrl + "/task?unassigned=true";
            List<Map<String, Object>> unassignedTasks = restTemplate.getForObject(unassignedUrl, List.class);

            if (unassignedTasks == null || unassignedTasks.isEmpty()) {
                return; // No tasks waiting to be dispatched
            }

            for (Map<String, Object> task : unassignedTasks) {
                String taskId = (String) task.get("id");
                String taskName = (String) task.get("name");

                // 2. Find which Candidate Group this specific task belongs to
                String identityLinksUrl = camundaUrl + "/task/" + taskId + "/identity-links?type=candidate";
                List<Map<String, Object>> links = restTemplate.getForObject(identityLinksUrl, List.class);

                if (links == null || links.isEmpty()) continue;

                String targetGroup = (String) links.get(0).get("groupId");
                if (targetGroup == null) continue;

                // 3. Fetch all active employees belonging to that group
                String usersUrl = camundaUrl + "/user?memberOfGroup=" + targetGroup;
                List<Map<String, Object>> groupMembers = restTemplate.getForObject(usersUrl, List.class);

                if (groupMembers == null || groupMembers.isEmpty()) {
                    System.err.println("❌ [AUTO-DISPATCH] Group '" + targetGroup + "' has 0 users!");
                    continue;
                }

                // 4. THE ALGORITHM: Find the employee with the lowest active task count
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

                // 5. Assign the task to the least-burdened user via REST API
                if (leastBurdenedUserId != null) {
                    String assignUrl = camundaUrl + "/task/" + taskId + "/assignee";
                    restTemplate.postForLocation(assignUrl, Map.of("userId", leastBurdenedUserId));
                    System.out.println("🤖 [AUTO-DISPATCH] Task '" + taskName + "' assigned to least-burdened user: -> " 
                                       + leastBurdenedUserId + " <- (Previous workload: " + lowestTaskCount + " tasks)");
                }
            }
        } catch (Exception e) {
            System.err.println("Auto-Dispatcher Error: " + e.getMessage());
        }
    }
}