package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_workflow_assignments",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_workflow", columnNames = {"user_id", "workflow_id"}),
    indexes = @Index(name = "idx_user_wf_workflow", columnList = "workflow_id")
)
public class UserWorkflowAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    public UserWorkflowAssignment() {}

    public UserWorkflowAssignment(String userId, String workflowId) {
        this.userId = userId;
        this.workflowId = workflowId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
