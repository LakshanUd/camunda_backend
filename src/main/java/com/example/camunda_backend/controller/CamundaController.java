package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CamundaController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 1. Get Active Processes (Process Definitions)
    @GetMapping("/processes")
    public Object getProcesses() {
        String url = camundaUrl + "/process-definition?latestVersion=true";
        // getForObject extracts JUST the body, stripping duplicate headers
        return restTemplate.getForObject(url, Object.class);
    }

    // General tasks endpoint
    @GetMapping("/tasks")
    public Object getAllTasks() {
        String url = camundaUrl + "/task";
        return restTemplate.getForObject(url, Object.class);
    }

    // 2. Get Active Tasks
    @GetMapping("/tasks/active")
    public Object getActiveTasks() {
        String url = camundaUrl + "/task";
        return restTemplate.getForObject(url, Object.class);
    }

    // 3. Get Completed Tasks
    @GetMapping("/tasks/completed")
    public Object getCompletedTasks() {
        String url = camundaUrl + "/history/task?finished=true";
        return restTemplate.getForObject(url, Object.class);
    }

    // 4. Get Form Variables for a specific task
    @GetMapping("/tasks/{taskId}/variables")
    public Object getTaskVariables(@PathVariable String taskId) {
        String url = camundaUrl + "/task/" + taskId + "/variables";
        return restTemplate.getForObject(url, Object.class);
    }

    // 5. Submit Task Form
    @PostMapping("/tasks/{taskId}/submit")
    public Object submitTask(@PathVariable String taskId, @RequestBody Map<String, Object> variables) {
        String url = camundaUrl + "/task/" + taskId + "/submit-form";
        return restTemplate.postForObject(url, variables, Object.class);
    }

    // 6. Get running instance count for a specific process
    @GetMapping("/processes/{id}/instances/count")
    public Object getInstanceCount(@PathVariable String id) {
        String url = camundaUrl + "/process-instance/count?processDefinitionId=" + id;
        return restTemplate.getForObject(url, Object.class);
    }

    // 7. Get deployed form schema for a task (Used by bpmn-io)
    @GetMapping("/tasks/{taskId}/form-schema")
    public Object getFormSchema(@PathVariable String taskId) {
        // Camunda 7 endpoint for pulling the JSON structure of a modeled form
        String url = camundaUrl + "/task/" + taskId + "/deployed-form";
        return restTemplate.getForObject(url, Object.class);
    }
}