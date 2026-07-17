package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        try {
            // Step 1: Verify credentials against Camunda Identity Service
            String verifyUrl = camundaUrl + "/identity/verify";
            Map<String, String> verifyPayload = Map.of("username", username, "password", password);
            Map<?, ?> verifyResult = restTemplate.postForObject(verifyUrl, verifyPayload, Map.class);

            if (verifyResult == null || !Boolean.TRUE.equals(verifyResult.get("authenticated"))) {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
            }

            // Step 2: Fetch assigned groups using the correct Camunda REST endpoint
            String groupsUrl = camundaUrl + "/group?member=" + username;
            List<?> groupsList = restTemplate.getForObject(groupsUrl, List.class);

            List<String> groupIds = new ArrayList<>();
            boolean isAdmin = false;

            if (groupsList != null) {
                for (Object groupObj : groupsList) {
                    if (groupObj instanceof Map) {
                        String groupId = (String) ((Map<?, ?>) groupObj).get("id");
                        if (groupId != null) {
                            groupIds.add(groupId);
                            if ("camunda-admin".equals(groupId)) {
                                isAdmin = true;
                            }
                        }
                    }
                }
            }

            // Step 3: Return clean security context to Angular
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("userId", username);
            sessionData.put("groups", groupIds);
            sessionData.put("isAdmin", isAdmin);

            return ResponseEntity.ok(sessionData);

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // Captures exact HTTP errors from Camunda if an endpoint fails
            System.err.println("Camunda API Error [" + e.getStatusCode() + "]: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", "Camunda authentication error", "details", e.getResponseBodyAsString()));
        } catch (Exception e) {
            System.err.println("Login Backend Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Authentication service failure", "error", e.getMessage()));
        }
    }
}