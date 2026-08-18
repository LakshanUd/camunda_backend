package com.example.camunda_backend.controller;

import com.example.camunda_backend.entity.TaskAssignmentRule;
import com.example.camunda_backend.entity.UserWorkflowAssignment;
import com.example.camunda_backend.repository.TaskAssignmentRuleRepository;
import com.example.camunda_backend.repository.UserWorkflowAssignmentRepository;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
public class WorkflowAssignmentController {

    private final UserWorkflowAssignmentRepository userWfRepo;
    private final TaskAssignmentRuleRepository taskRuleRepo;
    private final RepositoryService repositoryService;

    // Spring Boot automatically injects the live Camunda services here
    public WorkflowAssignmentController(UserWorkflowAssignmentRepository userWfRepo, 
                                        TaskAssignmentRuleRepository taskRuleRepo,
                                        RepositoryService repositoryService) {
        this.userWfRepo = userWfRepo;
        this.taskRuleRepo = taskRuleRepo;
        this.repositoryService = repositoryService;
    }

    // ==========================================
    // LIVE CAMUNDA DATA EXTRACTION
    // ==========================================
    
    @GetMapping("/available-workflows")
    public ResponseEntity<List<Map<String, String>>> getLiveWorkflows() {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .active()
                .list();

        List<Map<String, String>> result = new ArrayList<>();
        for (ProcessDefinition def : definitions) {
            Map<String, String> map = new HashMap<>();
            map.put("id", def.getKey()); 
            map.put("name", def.getName() != null ? def.getName() : def.getKey());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/available-workflows/{workflowKey}/tasks")
    public ResponseEntity<List<Map<String, String>>> getLiveTasksForWorkflow(@PathVariable String workflowKey) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(workflowKey)
                .latestVersion()
                .singleResult();

        if (processDefinition == null) {
            return ResponseEntity.notFound().build();
        }

        BpmnModelInstance modelInstance = repositoryService.getBpmnModelInstance(processDefinition.getId());
        Collection<UserTask> userTasks = modelInstance.getModelElementsByType(UserTask.class);

        List<Map<String, String>> result = new ArrayList<>();
        for (UserTask task : userTasks) {
            Map<String, String> map = new HashMap<>();
            map.put("id", task.getId()); 
            map.put("name", task.getName() != null ? task.getName() : task.getId());
            result.add(map);
        }
        
        return ResponseEntity.ok(result);
    }

    // ==========================================
    // ASSIGNMENT ROUTING 
    // ==========================================

    @PostMapping("/user/{userId}/workflows")
    public ResponseEntity<?> assignWorkflowsToUser(@PathVariable String userId, @RequestBody Map<String, List<String>> payload) {
        List<String> workflowIds = payload.get("workflowIds");
        userWfRepo.deleteByUserId(userId);
        List<UserWorkflowAssignment> newAssignments = workflowIds.stream()
            .map(wfId -> new UserWorkflowAssignment(userId, wfId))
            .collect(Collectors.toList());
        userWfRepo.saveAll(newAssignments);
        return ResponseEntity.ok(Map.of("message", "Workflows successfully assigned to " + userId));
    }

    @GetMapping("/workflow/{workflowId}/users")
    public ResponseEntity<List<UserWorkflowAssignment>> getUsersForWorkflow(@PathVariable String workflowId) {
        return ResponseEntity.ok(userWfRepo.findByWorkflowId(workflowId));
    }

    @PostMapping("/workflow/{workflowId}/users")
    public ResponseEntity<?> addUserToWorkflow(@PathVariable String workflowId, @RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");
        userWfRepo.save(new UserWorkflowAssignment(userId, workflowId));
        return ResponseEntity.ok(Map.of("message", "User " + userId + " added to workflow " + workflowId));
    }

    @DeleteMapping("/workflow/{workflowId}/users/{userId}")
    public ResponseEntity<?> removeUserFromWorkflow(@PathVariable String workflowId, @PathVariable String userId) {
        userWfRepo.deleteByWorkflowIdAndUserId(workflowId, userId);
        return ResponseEntity.ok(Map.of("message", "User removed from workflow"));
    }

    @GetMapping("/workflow/{workflowId}/tasks/{taskId}/rules")
    public ResponseEntity<List<TaskAssignmentRule>> getRulesForTask(@PathVariable String workflowId, @PathVariable String taskId) {
        return ResponseEntity.ok(taskRuleRepo.findByWorkflowIdAndTaskId(workflowId, taskId));
    }

    @PostMapping("/workflow/{workflowId}/tasks/{taskId}/rules")
    public ResponseEntity<?> addRuleToTask(@PathVariable String workflowId, @PathVariable String taskId, @RequestBody TaskAssignmentRule rule) {
        rule.setWorkflowId(workflowId);
        rule.setTaskId(taskId);
        if ("SELECT".equals(rule.getAssignmentType()) && (rule.getAssigneeUserId() == null || rule.getAssigneeUserId().isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assignee required for SELECT type"));
        }
        if (!"SELECT".equals(rule.getAssignmentType())) {
            rule.setAssigneeUserId(null); 
        }
        taskRuleRepo.save(rule);
        return ResponseEntity.ok(Map.of("message", "Task assignment rule saved successfully"));
    }

    @DeleteMapping("/task-rule/{ruleId}")
    public ResponseEntity<?> deleteTaskRule(@PathVariable Long ruleId) {
        taskRuleRepo.deleteById(ruleId);
        return ResponseEntity.ok(Map.of("message", "Rule deleted"));
    }
}