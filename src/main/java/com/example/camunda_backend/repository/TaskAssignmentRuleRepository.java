package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.TaskAssignmentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskAssignmentRuleRepository extends JpaRepository<TaskAssignmentRule, Long> {

    List<TaskAssignmentRule> findByWorkflowId(String workflowId);

    List<TaskAssignmentRule> findByWorkflowIdIn(Collection<String> workflowIds);

    Optional<TaskAssignmentRule> findByWorkflowIdAndTaskId(String workflowId, String taskId);

    Optional<TaskAssignmentRule> findByWorkflowIdInAndTaskId(Collection<String> workflowIds, String taskId);

    @Transactional
    void deleteByWorkflowIdAndTaskId(String workflowId, String taskId);

    @Transactional
    void deleteByWorkflowIdInAndTaskId(Collection<String> workflowIds, String taskId);

    @Transactional
    void deleteByWorkflowId(String workflowId);
}
