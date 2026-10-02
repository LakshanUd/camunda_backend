package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.WorkflowAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for WorkflowAuthorization.
 *
 * IMPORTANT: The 'group' field is a @ManyToOne relationship, so Spring Data JPA
 * derived queries must use the underscore traversal notation (Group_Id) for the
 * nested 'group.id' field. Alternatively we use explicit @Query JPQL.
 */
@Repository
public interface WorkflowAuthorizationRepository extends JpaRepository<WorkflowAuthorization, Long> {

    List<WorkflowAuthorization> findByWorkflowKey(String workflowKey);

    List<WorkflowAuthorization> findByUserId(String userId);

    List<WorkflowAuthorization> findByGroupId(String groupId);

    Optional<WorkflowAuthorization> findByWorkflowKeyAndUserId(String workflowKey, String userId);

    Optional<WorkflowAuthorization> findByWorkflowKeyAndGroupId(String workflowKey, String groupId);

    boolean existsByWorkflowKeyAndUserId(String workflowKey, String userId);

    boolean existsByWorkflowKeyAndGroupId(String workflowKey, String groupId);

    @Transactional
    void deleteByWorkflowKeyAndUserId(String workflowKey, String userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM WorkflowAuthorization wa WHERE wa.workflowKey = :workflowKey AND wa.groupId = :groupId")
    void deleteByWorkflowKeyAndGroupId(@Param("workflowKey") String workflowKey, @Param("groupId") String groupId);

    @Transactional
    void deleteByWorkflowKey(String workflowKey);

    /**
     * Returns all user IDs (direct user-level authorizations) for a workflow.
     * Used by DYNAMIC_USER routing to build the candidate pool.
     */
    @Query("SELECT wa.userId FROM WorkflowAuthorization wa WHERE wa.workflowKey = :workflowKey AND wa.userId IS NOT NULL")
    List<String> findAuthorizedUserIdsByWorkflowKey(@Param("workflowKey") String workflowKey);

    /**
     * Returns all group IDs authorized for a workflow.
     */
    @Query("SELECT wa.groupId FROM WorkflowAuthorization wa WHERE wa.workflowKey = :workflowKey AND wa.groupId IS NOT NULL")
    List<String> findAuthorizedGroupIdsByWorkflowKey(@Param("workflowKey") String workflowKey);
}
