package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "task_assignment_rules",
    uniqueConstraints = @UniqueConstraint(name = "uk_wf_task_rule", columnNames = {"workflow_id", "task_id"}),
    indexes = @Index(name = "idx_task_rule_lookup", columnList = "workflow_id, task_id")
)
public class TaskAssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    @Column(name = "assignment_type", nullable = false, length = 20)
    private String assignmentType; // "STAR", "DYNAMIC", "SELECT"

    @Column(name = "assignee_user_id", length = 36)
    private String assigneeUserId; // UUID of assigned user if assignmentType == 'SELECT'

    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public TaskAssignmentRule() {}

    public TaskAssignmentRule(String workflowId, String taskId, String assignmentType, String assigneeUserId) {
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.assignmentType = assignmentType;
        this.assigneeUserId = assigneeUserId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }

    public String getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(String assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
