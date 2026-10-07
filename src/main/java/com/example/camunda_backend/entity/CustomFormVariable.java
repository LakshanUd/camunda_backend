package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores individual normalized form field variables for SQL querying, reporting, and search.
 */
@Entity
@Table(
    name = "custom_form_variables",
    indexes = {
        @Index(name = "idx_cfv_task_id", columnList = "task_id"),
        @Index(name = "idx_cfv_proc_inst_id", columnList = "process_instance_id"),
        @Index(name = "idx_cfv_name", columnList = "variable_name")
    }
)
public class CustomFormVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "variable_name", length = 150, nullable = false)
    private String variableName;

    @Column(name = "variable_type", length = 50)
    private String variableType;

    @Lob
    @Column(name = "variable_value", columnDefinition = "LONGTEXT")
    private String variableValue;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public CustomFormVariable() {}

    public CustomFormVariable(Long submissionId, String taskId, String processInstanceId,
                              String variableName, String variableType, String variableValue) {
        this.submissionId = submissionId;
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.variableName = variableName;
        this.variableType = variableType;
        this.variableValue = variableValue;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getVariableType() { return variableType; }
    public void setVariableType(String variableType) { this.variableType = variableType; }

    public String getVariableValue() { return variableValue; }
    public void setVariableValue(String variableValue) { this.variableValue = variableValue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
