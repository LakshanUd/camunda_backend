package com.example.camunda_backend.listener;

import com.example.camunda_backend.entity.TaskAssignmentRule;
import com.example.camunda_backend.entity.UserWorkflowAssignment;
import com.example.camunda_backend.repository.TaskAssignmentRuleRepository;
import com.example.camunda_backend.repository.UserWorkflowAssignmentRepository;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component("dynamicAssignmentListener")
public class DynamicTaskAssignmentListener implements TaskListener {

    private final TaskAssignmentRuleRepository ruleRepository;
    private final UserWorkflowAssignmentRepository userWorkflowRepo;

    public DynamicTaskAssignmentListener(TaskAssignmentRuleRepository ruleRepository,
                                         UserWorkflowAssignmentRepository userWorkflowRepo) {
        this.ruleRepository = ruleRepository;
        this.userWorkflowRepo = userWorkflowRepo;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        // 1. Extract Workflow ID and Task ID
        // Camunda formats process IDs as "workflowId:version:hash". We just want the base name.
        String fullProcessId = delegateTask.getProcessDefinitionId();
        String workflowId = fullProcessId.split(":")[0]; 
        String taskId = delegateTask.getTaskDefinitionKey();

        // 2. Fetch the assignment rules for this specific task from MySQL
        List<TaskAssignmentRule> rules = ruleRepository.findByWorkflowIdAndTaskId(workflowId, taskId);

        if (rules.isEmpty()) {
            System.out.println("[ROUTING] No dynamic assignment rules found for task: " + taskId);
            return;
        }

        // Apply the first matching rule
        TaskAssignmentRule rule = rules.get(0);
        
        switch (rule.getAssignmentType()) {
            case "SELECT":
                // Route to the exact user specified in the rule
                delegateTask.setAssignee(rule.getAssigneeUserId());
                System.out.println("[ROUTING] Task " + taskId + " assigned to exact user: " + rule.getAssigneeUserId());
                break;

            case "STAR":
                // Route to the user who initiated the workflow
                // (Requires the frontend to pass {"variables": {"initiator": {"value": "username"}}} when starting the process)
                Object initiatorObj = delegateTask.getExecution().getVariable("initiator");
                if (initiatorObj != null) {
                    delegateTask.setAssignee(initiatorObj.toString());
                    System.out.println("[ROUTING] Task " + taskId + " assigned to workflow initiator: " + initiatorObj);
                } else {
                    System.err.println("[ROUTING ERROR] STAR rule failed: 'initiator' variable not found in process.");
                }
                break;

            case "DYNAMIC":
                // Route dynamically by picking from the pool of eligible workflow users
                List<UserWorkflowAssignment> eligibleUsers = userWorkflowRepo.findByWorkflowId(workflowId);
                
                if (!eligibleUsers.isEmpty()) {
                    // Simple Random Load Balancer
                    int randomIndex = new Random().nextInt(eligibleUsers.size());
                    String assignedUser = eligibleUsers.get(randomIndex).getUserId();
                    delegateTask.setAssignee(assignedUser);
                    System.out.println("[ROUTING] Task " + taskId + " dynamically load-balanced to: " + assignedUser);
                } else {
                    System.err.println("[ROUTING ERROR] DYNAMIC rule failed: No users are mapped to workflow " + workflowId);
                }
                break;
        }
    }
}