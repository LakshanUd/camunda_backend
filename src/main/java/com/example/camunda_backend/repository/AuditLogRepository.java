// 1. AuditLogRepository.java
package com.example.camunda_backend.repository;
import com.example.camunda_backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
    List<AuditLog> findByActionTypeOrderByTimestampDesc(String actionType);
}