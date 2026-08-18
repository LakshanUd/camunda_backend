package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/authorizations")
public class AuthorizationController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Mapping of your 19 separate API sections to Camunda Resource Type IDs
    private static final Map<String, Integer> SECTION_MAP = new HashMap<>() {{
        put("application", 0);
        put("user", 1);
        put("group", 2);
        put("group-membership", 3);
        put("authorization", 4);
        put("filter", 5);
        put("process-definition", 6);
        put("process-instance", 7);
        put("task", 8);
        put("deployment", 9);
        put("decision-definition", 10);
        put("tenant", 11);
        put("tenant-membership", 12);
        put("batch", 13);
        put("decision-requirements-definition", 14);
        // Note: 15 is Report and 16 is Dashboard in Camunda
        put("operation-log", 17);
        put("historic-task-instance", 18);
        put("historic-process-instance", 19);
        put("system", 20);
    }};

    private int getResourceTypeId(String section) {
        Integer typeId = SECTION_MAP.get(section.toLowerCase());
        if (typeId == null) {
            throw new IllegalArgumentException("Invalid authorization section: " + section);
        }
        return typeId;
    }

    // 1. VIEW: Get authorizations for a specific section (e.g., GET /api/authorizations/task)
    @GetMapping("/{section}")
    public ResponseEntity<?> getSectionAuthorizations(@PathVariable String section) {
        try {
            int resourceType = getResourceTypeId(section);
            String url = camundaUrl + "/authorization?resourceType=" + resourceType;
            Object result = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. CREATE: Create authorization in a specific section (e.g., POST /api/authorizations/task/create)
    @PostMapping("/{section}/create")
    public ResponseEntity<?> createSectionAuthorization(@PathVariable String section, @RequestBody Map<String, Object> authData) {
        try {
            int resourceType = getResourceTypeId(section);
            int authType = Integer.parseInt(String.valueOf(authData.get("type")));

            // Sanitize IDs (empty strings must become null in Camunda DB)
            String userId = sanitizeString((String) authData.get("userId"));
            String groupId = sanitizeString((String) authData.get("groupId"));
            String resourceId = sanitizeString((String) authData.get("resourceId"));
            if (resourceId == null) resourceId = "*";

            // Mutual exclusivity rule
            if (userId != null && groupId != null) groupId = null;

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", authType);
            payload.put("permissions", authData.get("permissions"));
            payload.put("resourceType", resourceType); // Force correct section ID
            payload.put("resourceId", resourceId);
            payload.put("userId", userId);
            payload.put("groupId", groupId);

            String url = camundaUrl + "/authorization/create";
            Object result = restTemplate.postForObject(url, payload, Object.class);
            return ResponseEntity.ok(result);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. EDIT: Update an existing authorization (e.g., PUT /api/authorizations/task/{id})
    @PutMapping("/{section}/{id}")
    public ResponseEntity<?> updateSectionAuthorization(@PathVariable String section, @PathVariable String id, @RequestBody Map<String, Object> authData) {
        try {
            int resourceType = getResourceTypeId(section);
            int authType = Integer.parseInt(String.valueOf(authData.get("type")));

            String userId = sanitizeString((String) authData.get("userId"));
            String groupId = sanitizeString((String) authData.get("groupId"));
            String resourceId = sanitizeString((String) authData.get("resourceId"));
            if (resourceId == null) resourceId = "*";

            if (userId != null && groupId != null) groupId = null;

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", authType);
            payload.put("permissions", authData.get("permissions"));
            payload.put("resourceType", resourceType);
            payload.put("resourceId", resourceId);
            payload.put("userId", userId);
            payload.put("groupId", groupId);

            String url = camundaUrl + "/authorization/" + id;
            restTemplate.put(url, payload);
            return ResponseEntity.ok(Map.of("status", "updated", "id", id));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. DELETE: Remove an authorization from a section (e.g., DELETE /api/authorizations/task/{id})
    @DeleteMapping("/{section}/{id}")
    public ResponseEntity<?> deleteSectionAuthorization(@PathVariable String section, @PathVariable String id) {
        try {
            // Verify section exists before attempting delete
            getResourceTypeId(section);
            String url = camundaUrl + "/authorization/" + id;
            restTemplate.delete(url);
            return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private String sanitizeString(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        return input.trim();
    }
}