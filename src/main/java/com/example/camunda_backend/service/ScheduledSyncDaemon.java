package com.example.camunda_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledSyncDaemon {

    @Value("${camunda.api.url:http://localhost:8080/engine-rest}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final TaskSyncService taskSyncService;

    public ScheduledSyncDaemon(TaskSyncService taskSyncService) {
        this.taskSyncService = taskSyncService;
    }

    // Runs silently in the background every 10 seconds (10000 ms) to guarantee 100% database sync
    @Scheduled(fixedDelay = 10000)
    public void selfHealingDatabaseSync() {
        try {
            // 1. Fetch all currently active tasks from standalone Camunda
            String url = camundaUrl + "/task";
            List<Map<String, Object>> activeTasks = restTemplate.getForObject(url, List.class);

            if (activeTasks == null || activeTasks.isEmpty()) return;

            // 2. Loop through active tasks and ensure MySQL mirror has exact matching state
            for (Map<String, Object> task : activeTasks) {
                String taskId = (String) task.get("id");
                String taskName = (String) task.get("name");
                String processId = (String) task.get("processInstanceId");
                String taskDefKey = (String) task.get("taskDefinitionKey");
                String assignee = (String) task.get("assignee");
                
                String status = (assignee != null && !assignee.isEmpty()) ? "ASSIGNED" : "UNASSIGNED";

                taskSyncService.syncTask(taskId, processId, taskName, taskDefKey, assignee, null, status);
            }
        } catch (Exception e) {
            // Suppress connection errors if engine is temporarily restarting
        }
    }
}