package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.UserGroupMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for UserGroupMapping.
 *
 * IMPORTANT: The 'group' field is a @ManyToOne relationship.
 * Spring Data JPA derived queries must use Group_Id (underscore traversal)
 * for the nested group.id field. Bulk deletes use explicit @Query / @Modifying.
 */
@Repository
public interface UserGroupMappingRepository extends JpaRepository<UserGroupMapping, Long> {

    List<UserGroupMapping> findByUserId(String userId);

    // Traverse group.id using Spring Data underscore notation
    List<UserGroupMapping> findByGroup_Id(Long groupId);

    boolean existsByUserIdAndGroup_Id(String userId, Long groupId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserGroupMapping m WHERE m.userId = :userId AND m.group.id = :groupId")
    void deleteByUserIdAndGroupId(@Param("userId") String userId, @Param("groupId") Long groupId);

    @Transactional
    void deleteByUserId(String userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserGroupMapping m WHERE m.group.id = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    /**
     * Returns all user IDs that belong to a given group.
     * Used by TaskInterceptorService for SELECT_GROUP and DYNAMIC_USER routing.
     */
    @Query("SELECT m.userId FROM UserGroupMapping m WHERE m.group.id = :groupId")
    List<String> findUserIdsByGroupId(@Param("groupId") Long groupId);
}
