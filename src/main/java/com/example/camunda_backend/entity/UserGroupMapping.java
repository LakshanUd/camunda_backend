package com.example.camunda_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Junction table mapping a User (by UUID) to an AppGroup.
 * Replaces the old UserWorkflowAssignment concept for group identity.
 */
@Entity
@Table(
    name = "user_group_mapping",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_group", columnNames = {"user_id", "group_id"}),
    indexes = {
        @Index(name = "idx_ugm_user",  columnList = "user_id"),
        @Index(name = "idx_ugm_group", columnList = "group_id")
    }
)
public class UserGroupMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID of the user from the app_users table */
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ugm_group"))
    private AppGroup group;

    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    public UserGroupMapping() {}

    public UserGroupMapping(String userId, AppGroup group) {
        this.userId = userId;
        this.group = group;
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

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public AppGroup getGroup() { return group; }
    public void setGroup(AppGroup group) { this.group = group; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
