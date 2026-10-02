package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Defines HOW a specific BPMN UserTask step is routed when it is created.
 *
 * routing_type controls the assignment strategy applied by the TaskInterceptorService:
 *   STAR         — Assigns to the workflow initiator (reads 'starterUserId' process variable)
 *   DYNAMIC_USER — Selects from authorized users based on lowest active task count (load balancing)
 *   SELECT_USER  — Statically assigns to a specific pre-configured user
 *   SELECT_GROUP — Sets a candidateGroup so any member of the group can claim the task
 *
 * Replaces the old TaskAssignmentRule entity.
 */
@Entity
@Table(
    name = "task_routing_rules",
    uniqueConstraints = @UniqueConstraint(name = "uk_task_routing", columnNames = {"workflow_key", "task_id"}),
    indexes = {
        @Index(name = "idx_trr_lookup", columnList = "workflow_key, task_id"),
        @Index(name = "idx_trr_group",  columnList = "target_group_id")
    }
)
public class TaskRoutingRule {

    /** Enum of supported routing strategies */
    public enum RoutingType {
        STAR,
        DYNAMIC_USER,
        SELECT_USER,
        SELECT_GROUP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Camunda process definition key (normalized — no version suffix) */
    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    /** The BPMN element ID of the UserTask (e.g., "Task_ReviewRequest") */
    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    /** The routing strategy to apply when this task is created */
    @Enumerated(EnumType.STRING)
    @Column(name = "routing_type", nullable = false, length = 20)
    private RoutingType routingType = RoutingType.STAR;

    /**
     * Required when routingType = SELECT_USER.
     * Stores the UUID of the target user from app_users.
     */
    @Column(name = "target_user_id", length = 36)
    private String targetUserId;

    /**
     * Required when routingType = SELECT_GROUP.
     * ID of the group that will receive the task as a candidateGroup (e.g., "accounting").
     */
    @Column(name = "target_group_id", length = 64)
    private String targetGroupId;

    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    public TaskRoutingRule() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public RoutingType getRoutingType() { return routingType; }
    public void setRoutingType(RoutingType routingType) { this.routingType = routingType; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public String getTargetGroupId() { return targetGroupId; }
    public void setTargetGroupId(String targetGroupId) { this.targetGroupId = targetGroupId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
