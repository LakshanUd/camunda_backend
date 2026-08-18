package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.UserWorkflowAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional;
import java.util.List;

@Repository
public interface UserWorkflowAssignmentRepository extends JpaRepository<UserWorkflowAssignment, Long> {
    List<UserWorkflowAssignment> findByUserId(String userId);
    List<UserWorkflowAssignment> findByWorkflowId(String workflowId);
    
    @Transactional
    void deleteByUserId(String userId);
    
    @Transactional
    void deleteByWorkflowIdAndUserId(String workflowId, String userId);
}