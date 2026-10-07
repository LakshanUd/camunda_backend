package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks user tasks, assignment states, and execution metrics in MySQL.
 */
@Entity
@Table(
    name = "custom_tasks",
    indexes = {
        @Index(name = "idx_ct_proc_inst", columnList = "process_instance_id"),
        @Index(name = "idx_ct_assignee", columnList = "assignee"),
        @Index(name = "idx_ct_cand_group", columnList = "candidate_group"),
        @Index(name = "idx_ct_status", columnList = "status")
    }
)
public class CustomTaskRecord {

    @Id
    @Column(length = 64, nullable = false)
    private String id; // Camunda taskId

    @Column(name = "task_definition_key", length = 100)
    private String taskDefinitionKey;

    @Column(length = 255)
    private String name;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "process_definition_key", length = 100)
    private String processDefinitionKey;

    @Column(name = "process_definition_name", length = 255)
    private String processDefinitionName;

    @Column(length = 100)
    private String assignee;

    @Column(name = "candidate_group", length = 100)
    private String candidateGroup;

    @Column(length = 30)
    private String status; // ACTIVE, CLAIMED, UNCLAIMED, COMPLETED

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CustomTaskRecord() {}

    public CustomTaskRecord(String id, String taskDefinitionKey, String name, String processInstanceId,
                            String processDefinitionKey, String assignee, String candidateGroup, String status) {
        this.id = id;
        this.taskDefinitionKey = taskDefinitionKey;
        this.name = name;
        this.processInstanceId = processInstanceId;
        this.processDefinitionKey = processDefinitionKey;
        this.assignee = assignee;
        this.candidateGroup = candidateGroup;
        this.status = status != null ? status : "ACTIVE";
        this.startTime = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.startTime == null) {
            this.startTime = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskDefinitionKey() { return taskDefinitionKey; }
    public void setTaskDefinitionKey(String taskDefinitionKey) { this.taskDefinitionKey = taskDefinitionKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public String getProcessDefinitionName() { return processDefinitionName; }
    public void setProcessDefinitionName(String processDefinitionName) { this.processDefinitionName = processDefinitionName; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getCandidateGroup() { return candidateGroup; }
    public void setCandidateGroup(String candidateGroup) { this.candidateGroup = candidateGroup; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
