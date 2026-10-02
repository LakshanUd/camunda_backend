package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.TaskRoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRoutingRuleRepository extends JpaRepository<TaskRoutingRule, Long> {

    List<TaskRoutingRule> findByWorkflowKey(String workflowKey);

    Optional<TaskRoutingRule> findByWorkflowKeyAndTaskId(String workflowKey, String taskId);

    boolean existsByWorkflowKeyAndTaskId(String workflowKey, String taskId);

    @Transactional
    void deleteByWorkflowKeyAndTaskId(String workflowKey, String taskId);

    @Transactional
    void deleteByWorkflowKey(String workflowKey);
}
