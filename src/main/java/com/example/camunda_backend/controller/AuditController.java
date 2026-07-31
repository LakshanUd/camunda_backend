package com.example.camunda_backend.controller;

import com.example.camunda_backend.entity.AuditLog;
import com.example.camunda_backend.repository.AuditLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // GET /api/audit/logs - Fetch system activity logs with optional user or action filtering
    @GetMapping("/logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String actionType
    ) {
        try {
            List<AuditLog> logs;
            if (userId != null && !userId.trim().isEmpty()) {
                logs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId.trim());
            } else if (actionType != null && !actionType.trim().isEmpty()) {
                logs = auditLogRepository.findByActionTypeOrderByTimestampDesc(actionType.trim());
            } else {
                // Default: Fetch all logs sorted by newest first
                logs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
            }
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}