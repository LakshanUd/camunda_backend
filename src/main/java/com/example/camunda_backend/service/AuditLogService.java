package com.example.camunda_backend.service;

import com.example.camunda_backend.entity.AuditLog;
import com.example.camunda_backend.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // @Async forces Spring Boot to run this database write in a separate thread pool!
    @Async
    public void recordAuditLog(String userId, String actionType, String targetResource, 
                               String resourceId, String oldValue, String newValue, String ipAddress) {
        try {
            AuditLog log = new AuditLog(userId, actionType, targetResource, resourceId, oldValue, newValue, ipAddress);
            auditLogRepository.save(log);
            System.out.println("🛡️ [AUDIT TRAIL] Recorded: [" + actionType + "] on [" + targetResource + "] by User: -> " + userId + " <-");
        } catch (Exception e) {
            System.err.println("❌ [AUDIT TRAIL ERROR] Failed to write log to MySQL: " + e.getMessage());
        }
    }
}