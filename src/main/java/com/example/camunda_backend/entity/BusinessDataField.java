package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_business_data", indexes = {
    @Index(name = "idx_process_instance", columnList = "process_instance_id"),
    @Index(name = "idx_field_key", columnList = "field_key")
})
public class BusinessDataField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false, length = 100)
    private String processInstanceId;

    @Column(name = "task_id", length = 100)
    private String taskId;

    @Column(name = "field_key", nullable = false, length = 150)
    private String fieldKey; // e.g., "customerName", "loanAmount", "department"

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "field_type", length = 50)
    private String fieldType; // e.g., "String", "Long", "Boolean"

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BusinessDataField() {}

    public BusinessDataField(String processInstanceId, String taskId, String fieldKey, String fieldValue, String fieldType, String updatedBy) {
        this.processInstanceId = processInstanceId;
        this.taskId = taskId;
        this.fieldKey = fieldKey;
        this.fieldValue = fieldValue;
        this.fieldType = fieldType;
        this.updatedBy = updatedBy;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getFieldValue() { return fieldValue; }
    public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}