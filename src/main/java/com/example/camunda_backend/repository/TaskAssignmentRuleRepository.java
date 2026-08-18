package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.TaskAssignmentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskAssignmentRuleRepository extends JpaRepository<TaskAssignmentRule, Long> {
    List<TaskAssignmentRule> findByWorkflowIdAndTaskId(String workflowId, String taskId);
}