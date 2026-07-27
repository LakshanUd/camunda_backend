package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_task_mirror")
public class TaskMirror {

    @Id
    @Column(name = "task_id", length = 100)
    private String taskId; // Directly maps to Camunda's Task ID

    @Column(name = "process_instance_id", nullable = false, length = 100)
    private String processInstanceId;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "task_definition_key", length = 150)
    private String taskDefinitionKey;

    @Column(name = "assignee", length = 100)
    private String assignee;

    @Column(name = "candidate_group", length = 100)
    private String candidateGroup;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // e.g., "PENDING", "COMPLETED", "REASSIGNED"

    @Column(name = "completion_decision", length = 100)
    private String completionDecision; // e.g., "Approved", "Rejected"

    @Column(name = "completion_comment", columnDefinition = "TEXT")
    private String completionComment;

    public TaskMirror() {}

    // Getters and Setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskDefinitionKey() { return taskDefinitionKey; }
    public void setTaskDefinitionKey(String taskDefinitionKey) { this.taskDefinitionKey = taskDefinitionKey; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public String getCandidateGroup() { return candidateGroup; }
    public void setCandidateGroup(String candidateGroup) { this.candidateGroup = candidateGroup; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCompletionDecision() { return completionDecision; }
    public void setCompletionDecision(String completionDecision) { this.completionDecision = completionDecision; }
    public String getCompletionComment() { return completionComment; }
    public void setCompletionComment(String completionComment) { this.completionComment = completionComment; }
}