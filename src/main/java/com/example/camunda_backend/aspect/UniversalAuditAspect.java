package com.example.camunda_backend.aspect;

import com.example.camunda_backend.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Arrays;
import java.util.Map;

@Aspect
@Component
public class UniversalAuditAspect {

    private final AuditLogService auditLogService;

    public UniversalAuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("execution(* com.example.camunda_backend.controller.*.*(..))")
    public Object interceptAndLog(ProceedingJoinPoint joinPoint) throws Throwable {
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String ipAddress = request != null ? request.getRemoteAddr() : "UNKNOWN_IP";
        String httpMethod = request != null ? request.getMethod() : "SYSTEM";

        // 1. Identify the Actor (User ID) from Headers, Params, or JSON Bodies
        String userId = "ANONYMOUS";
        if (request != null) {
            if (request.getHeader("X-User-Id") != null && !request.getHeader("X-User-Id").trim().isEmpty()) {
                userId = request.getHeader("X-User-Id");
            } else if (request.getParameter("userId") != null) {
                userId = request.getParameter("userId");
            } else if (request.getParameter("assignee") != null) {
                userId = request.getParameter("assignee");
            } else if (request.getParameter("username") != null) {
                userId = request.getParameter("username");
            }
        }
        if ("ANONYMOUS".equals(userId)) {
            userId = extractUserFromPayload(joinPoint.getArgs());
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String targetResource = signature.getDeclaringType().getSimpleName();
        String actionType = httpMethod + "_" + methodName.toUpperCase();

        // 2. Extract the exact Target Resource ID (e.g. Task UUID, Group ID)
        String resourceId = extractResourceId(joinPoint.getArgs(), signature.getParameterNames());
        String payloadSummary = Arrays.toString(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            auditLogService.recordAuditLog(userId, actionType, targetResource, resourceId, null, "SUCCESS: " + payloadSummary, ipAddress);
            return result;
        } catch (Throwable ex) {
            auditLogService.recordAuditLog(userId, actionType + "_FAILED", targetResource, resourceId, null, "ERROR: " + ex.getMessage(), ipAddress);
            throw ex;
        }
    }

    private String extractUserFromPayload(Object[] args) {
        if (args == null) return "ANONYMOUS";
        for (Object arg : args) {
            if (arg instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) arg;
                if (map.containsKey("username") && map.get("username") != null) return map.get("username").toString();
                if (map.containsKey("userId") && map.get("userId") != null) return map.get("userId").toString();
                if (map.containsKey("assignee") && map.get("assignee") != null) return map.get("assignee").toString();
            }
        }
        return "ANONYMOUS";
    }

    // UPGRADED: Smart Resource ID Extractor that ignores actor IDs and inspects JSON bodies
    private String extractResourceId(Object[] args, String[] paramNames) {
        if (args == null || paramNames == null) return null;
        
        // Step A: Check standard method parameters (like @PathVariable String taskId)
        for (int i = 0; i < paramNames.length; i++) {
            String name = paramNames[i].toLowerCase();
            
            // IGNORE actor parameters! Never treat the user performing the action as the target resource ID
            if (name.equals("userid") || name.equals("user_id") || name.equals("assignee") || name.equals("username")) {
                continue; 
            }

            // Exactly match true target resource IDs (taskId, groupId, tenantId, processId, or plain id)
            if ((name.endsWith("id") || name.equals("id") || name.equals("key")) && args[i] instanceof String && args[i] != null) {
                return (String) args[i];
            }
        }
        
        // Step B: If not found in URL parameters, look inside JSON request bodies (e.g., creating a group {"id": "accounting"})
        for (Object arg : args) {
            if (arg instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) arg;
                if (map.containsKey("taskId") && map.get("taskId") != null) return map.get("taskId").toString();
                if (map.containsKey("groupId") && map.get("groupId") != null) return map.get("groupId").toString();
                if (map.containsKey("tenantId") && map.get("tenantId") != null) return map.get("tenantId").toString();
                if (map.containsKey("id") && map.get("id") != null && !map.containsKey("username")) return map.get("id").toString();
            }
        }
        
        return null; // Correctly returns NULL for general list queries like GET_ALLUSERS or GET_MYTASKS
    }
}