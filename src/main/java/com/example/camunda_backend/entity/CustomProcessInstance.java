package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks the lifecycle and metadata of workflow process instances in MySQL.
 */
@Entity
@Table(
    name = "custom_process_instances",
    indexes = {
        @Index(name = "idx_cpi_def_key", columnList = "process_definition_key"),
        @Index(name = "idx_cpi_start_user", columnList = "start_user_id"),
        @Index(name = "idx_cpi_status", columnList = "status")
    }
)
public class CustomProcessInstance {

    @Id
    @Column(length = 64, nullable = false)
    private String id; // Camunda processInstanceId

    @Column(name = "process_definition_key", length = 100)
    private String processDefinitionKey;

    @Column(name = "process_definition_name", length = 255)
    private String processDefinitionName;

    @Column(name = "process_definition_version")
    private Integer processDefinitionVersion;

    @Column(name = "business_key", length = 255)
    private String businessKey;

    @Column(name = "start_user_id", length = 100)
    private String startUserId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 30)
    private String status; // ACTIVE, COMPLETED, CANCELLED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CustomProcessInstance() {}

    public CustomProcessInstance(String id, String processDefinitionKey, String processDefinitionName,
                                 Integer processDefinitionVersion, String businessKey, String startUserId,
                                 LocalDateTime startTime, String status) {
        this.id = id;
        this.processDefinitionKey = processDefinitionKey;
        this.processDefinitionName = processDefinitionName;
        this.processDefinitionVersion = processDefinitionVersion;
        this.businessKey = businessKey;
        this.startUserId = startUserId;
        this.startTime = startTime != null ? startTime : LocalDateTime.now();
        this.status = status != null ? status : "ACTIVE";
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

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public String getProcessDefinitionName() { return processDefinitionName; }
    public void setProcessDefinitionName(String processDefinitionName) { this.processDefinitionName = processDefinitionName; }

    public Integer getProcessDefinitionVersion() { return processDefinitionVersion; }
    public void setProcessDefinitionVersion(Integer processDefinitionVersion) { this.processDefinitionVersion = processDefinitionVersion; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public String getStartUserId() { return startUserId; }
    public void setStartUserId(String startUserId) { this.startUserId = startUserId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
