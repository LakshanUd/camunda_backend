package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
@PreAuthorize("hasRole('ADMIN')")
public class TenantController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 1. Get all tenants
    @GetMapping
    public ResponseEntity<?> getAllTenants() {
        try {
            String url = camundaUrl + "/tenant";
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response != null ? response : Collections.emptyList());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. Create a new tenant
    @PostMapping("/create")
    public ResponseEntity<?> createTenant(@RequestBody Map<String, String> tenantData) {
        try {
            String url = camundaUrl + "/tenant/create";
            Object response = restTemplate.postForObject(url, tenantData, Object.class);
            return ResponseEntity.ok(response != null ? response : Map.of("status", "Tenant created successfully"));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. Get a single tenant by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getTenant(@PathVariable String id) {
        try {
            String url = camundaUrl + "/tenant/" + id;
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. Update an existing tenant
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable String id, @RequestBody Map<String, String> tenantData) {
        try {
            String url = camundaUrl + "/tenant/" + id;
            restTemplate.put(url, tenantData);
            return ResponseEntity.ok(Map.of("status", "Tenant updated successfully", "id", id));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 5. Delete a tenant
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTenant(@PathVariable String id) {
        try {
            String url = camundaUrl + "/tenant/" + id;
            restTemplate.delete(url);
            return ResponseEntity.ok(Map.of("status", "Tenant deleted successfully", "id", id));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}