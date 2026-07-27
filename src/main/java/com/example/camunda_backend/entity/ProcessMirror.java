package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_process_mirror")
public class ProcessMirror {

    @Id
    @Column(name = "process_instance_id", length = 100)
    private String processInstanceId; // Directly maps to Camunda's runtime instance ID

    @Column(name = "process_definition_key", nullable = false, length = 150)
    private String processDefinitionKey;

    @Column(name = "business_key", length = 150)
    private String businessKey;

    @Column(name = "start_user_id", length = 100)
    private String startUserId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // e.g., "ACTIVE", "COMPLETED", "CANCELLED"

    public ProcessMirror() {}

    public ProcessMirror(String processInstanceId, String processDefinitionKey, String businessKey, String startUserId, LocalDateTime startTime, String status) {
        this.processInstanceId = processInstanceId;
        this.processDefinitionKey = processDefinitionKey;
        this.businessKey = businessKey;
        this.startUserId = startUserId;
        this.startTime = startTime;
        this.status = status;
    }

    // Getters and Setters
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getStartUserId() { return startUserId; }
    public void setStartUserId(String startUserId) { this.startUserId = startUserId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}