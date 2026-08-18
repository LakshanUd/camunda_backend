package com.example.camunda_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "task_assignment_rules")
public class TaskAssignmentRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String workflowId; 
    
    @Column(nullable = false)
    private String taskId; // e.g., "Activity_ReviewApplication"
    
    @Column(nullable = false)
    private String assignmentType; // "STAR", "DYNAMIC", or "SELECT"
    
    @Column(nullable = true)
    private String assigneeUserId; // Only populated if type is "SELECT"

    // Getters and Setters
    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAssignmentType() { return assignmentType; }
    public void setAssignmentType(String assignmentType) { this.assignmentType = assignmentType; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }
}