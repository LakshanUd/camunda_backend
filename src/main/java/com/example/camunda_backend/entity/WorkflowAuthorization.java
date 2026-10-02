package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Defines WHO is authorized to start or be assigned to a specific workflow.
 * Either userId OR groupId must be set (enforced by DB CHECK constraint and service layer).
 *
 * Replaces the old UserWorkflowAssignment entity with proper group support.
 */
@Entity
@Table(
    name = "workflow_authorizations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_wf_auth_user",  columnNames = {"workflow_key", "user_id"}),
        @UniqueConstraint(name = "uk_wf_auth_group", columnNames = {"workflow_key", "group_id"})
    },
    indexes = {
        @Index(name = "idx_wfa_workflow", columnList = "workflow_key"),
        @Index(name = "idx_wfa_user",     columnList = "user_id"),
        @Index(name = "idx_wfa_group",    columnList = "group_id")
    }
)
public class WorkflowAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Camunda process definition key (e.g., "leave-request-process") */
    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    /** Nullable: UUID of the specific user authorized for this workflow */
    @Column(name = "user_id", length = 36)
    private String userId;

    /** Nullable: ID of the group authorized for this workflow (from existing groups) */
    @Column(name = "group_id", length = 64)
    private String groupId;

    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    public WorkflowAuthorization() {}

    /** Constructor for user-based authorization */
    public WorkflowAuthorization(String workflowKey, String userId) {
        this.workflowKey = workflowKey;
        this.userId = userId;
        this.groupId = null;
    }

    /** Factory method for group-based authorization */
    public static WorkflowAuthorization forGroup(String workflowKey, String groupId) {
        WorkflowAuthorization auth = new WorkflowAuthorization();
        auth.workflowKey = workflowKey;
        auth.groupId = groupId;
        auth.userId = null;
        return auth;
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

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Returns true if this is a user-level authorization */
    public boolean isUserAuthorization()  { return userId != null && !userId.isBlank(); }

    /** Returns true if this is a group-level authorization */
    public boolean isGroupAuthorization() { return groupId != null && !groupId.isBlank(); }
}
