package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
@CrossOrigin(origins = "*") 
public class TenantController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 1. Get all tenants
    @GetMapping
    public Object getAllTenants() {
        String url = camundaUrl + "/tenant";
        return restTemplate.getForObject(url, Object.class);
    }

    // 2. Create a new tenant
    @PostMapping("/create")
    public Object createTenant(@RequestBody Map<String, String> tenantData) {
        String url = camundaUrl + "/tenant/create";
        // Camunda expects: { "id": "...", "name": "..." }
        return restTemplate.postForObject(url, tenantData, Object.class);
    }

    // 3. Get a single tenant by ID
    @GetMapping("/{id}")
    public Object getTenant(@PathVariable String id) {
        String url = camundaUrl + "/tenant/" + id;
        return restTemplate.getForObject(url, Object.class);
    }

    // 4. Update an existing tenant
    @PutMapping("/{id}")
    public void updateTenant(@PathVariable String id, @RequestBody Map<String, String> tenantData) {
        String url = camundaUrl + "/tenant/" + id;
        restTemplate.put(url, tenantData);
    }

    // 5. Delete a tenant
    @DeleteMapping("/{id}")
    public void deleteTenant(@PathVariable String id) {
        String url = camundaUrl + "/tenant/" + id;
        restTemplate.delete(url);
    }
}