package com.example.camunda_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_workflow_assignments")
public class UserWorkflowAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String workflowId; // e.g., "loan_approval_process"

    // Constructors
    public UserWorkflowAssignment() {}
    public UserWorkflowAssignment(String userId, String workflowId) {
        this.userId = userId;
        this.workflowId = workflowId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
}