package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores immutable form submission audit snapshots including full JSON data and form schema.
 */
@Entity
@Table(
    name = "custom_form_submissions",
    indexes = {
        @Index(name = "idx_cfs_task_id", columnList = "task_id"),
        @Index(name = "idx_cfs_proc_inst_id", columnList = "process_instance_id"),
        @Index(name = "idx_cfs_submitted_by", columnList = "submitted_by")
    }
)
public class CustomFormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "task_definition_key", length = 100)
    private String taskDefinitionKey;

    @Column(name = "task_name", length = 255)
    private String taskName;

    @Column(name = "form_key", length = 255)
    private String formKey;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Lob
    @Column(name = "form_data_json", columnDefinition = "LONGTEXT")
    private String formDataJson;

    @Lob
    @Column(name = "form_schema_json", columnDefinition = "LONGTEXT")
    private String formSchemaJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public CustomFormSubmission() {}

    public CustomFormSubmission(String taskId, String processInstanceId, String taskDefinitionKey,
                                String taskName, String formKey, String submittedBy,
                                String formDataJson, String formSchemaJson) {
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.taskDefinitionKey = taskDefinitionKey;
        this.taskName = taskName;
        this.formKey = formKey;
        this.submittedBy = submittedBy;
        this.formDataJson = formDataJson;
        this.formSchemaJson = formSchemaJson;
        this.submittedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
    }

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getTaskDefinitionKey() { return taskDefinitionKey; }
    public void setTaskDefinitionKey(String taskDefinitionKey) { this.taskDefinitionKey = taskDefinitionKey; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getFormDataJson() { return formDataJson; }
    public void setFormDataJson(String formDataJson) { this.formDataJson = formDataJson; }

    public String getFormSchemaJson() { return formSchemaJson; }
    public void setFormSchemaJson(String formSchemaJson) { this.formSchemaJson = formSchemaJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
