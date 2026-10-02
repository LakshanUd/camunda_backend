package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.UserWorkflowAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserWorkflowAssignmentRepository extends JpaRepository<UserWorkflowAssignment, Long> {

    List<UserWorkflowAssignment> findByWorkflowId(String workflowId);

    List<UserWorkflowAssignment> findByWorkflowIdIn(Collection<String> workflowIds);

    List<UserWorkflowAssignment> findByUserId(String userId);

    boolean existsByUserIdAndWorkflowId(String userId, String workflowId);

    @Transactional
    void deleteByWorkflowIdAndUserId(String workflowId, String userId);

    @Transactional
    void deleteByWorkflowIdInAndUserId(Collection<String> workflowIds, String userId);

    @Transactional
    void deleteByUserId(String userId);

    @Transactional
    void deleteByWorkflowId(String workflowId);
}
