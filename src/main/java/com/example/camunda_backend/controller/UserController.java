package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*") 
public class UserController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // --- PHASE 1: LOGIN ENDPOINT ---

    @PostMapping("/login")
    public Object verifyLogin(@RequestBody Map<String, String> credentials) {
        String url = camundaUrl + "/identity/verify";
        return restTemplate.postForObject(url, credentials, Object.class);
    }

    // --- PHASE 2: ADMIN USER MANAGEMENT ENDPOINTS ---

    // 1. Get all users
    @GetMapping
    public Object getAllUsers() {
        String url = camundaUrl + "/user";
        return restTemplate.getForObject(url, Object.class);
    }

    // 2. Create new user
    @PostMapping("/create")
    public Object createUser(@RequestBody Map<String, Object> payload) {
        String url = camundaUrl + "/user/create";
        return restTemplate.postForObject(url, payload, Object.class);
    }

    // 3. Get single user profile
    @GetMapping("/{id}/profile")
    public Object getUserProfile(@PathVariable String id) {
        String url = camundaUrl + "/user/" + id + "/profile";
        return restTemplate.getForObject(url, Object.class);
    }

    // 4. Update user profile
    @PutMapping("/{id}/profile")
    public void updateUserProfile(@PathVariable String id, @RequestBody Map<String, Object> profile) {
        String url = camundaUrl + "/user/" + id + "/profile";
        restTemplate.put(url, profile);
    }

    // 5. Update user credentials (password)
    @PutMapping("/{id}/credentials")
    public void updateUserCredentials(@PathVariable String id, @RequestBody Map<String, Object> credentials) {
        String url = camundaUrl + "/user/" + id + "/credentials";
        restTemplate.put(url, credentials);
    }

    // 6. Delete user
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable String id) {
        String url = camundaUrl + "/user/" + id;
        restTemplate.delete(url);
    }

    // --- PHASE 3: MEMBERSHIP MANAGEMENT ---

    // 7. List groups for a user
    @GetMapping("/{id}/groups")
    public Object getUserGroups(@PathVariable String id) {
        String url = camundaUrl + "/group?member=" + id;
        return restTemplate.getForObject(url, Object.class);
    }

    // 8. Add user to a group
    @PutMapping("/{id}/groups/{groupId}")
    public void addUserToGroup(@PathVariable String id, @PathVariable String groupId) {
        String url = camundaUrl + "/group/" + groupId + "/members/" + id;
        restTemplate.put(url, null);
    }

    // 9. Remove user from a group
    @DeleteMapping("/{id}/groups/{groupId}")
    public void removeUserFromGroup(@PathVariable String id, @PathVariable String groupId) {
        String url = camundaUrl + "/group/" + groupId + "/members/" + id;
        restTemplate.delete(url);
    }

    // 10. List tenants for a user
    @GetMapping("/{id}/tenants")
    public Object getUserTenants(@PathVariable String id) {
        String url = camundaUrl + "/tenant?userMember=" + id;
        return restTemplate.getForObject(url, Object.class);
    }

    // 11. Add user to a tenant
    @PutMapping("/{id}/tenants/{tenantId}")
    public void addUserToTenant(@PathVariable String id, @PathVariable String tenantId) {
        String url = camundaUrl + "/tenant/" + tenantId + "/user-members/" + id;
        restTemplate.put(url, null);
    }

    // 12. Remove user from a tenant
    @DeleteMapping("/{id}/tenants/{tenantId}")
    public void removeUserFromTenant(@PathVariable String id, @PathVariable String tenantId) {
        String url = camundaUrl + "/tenant/" + tenantId + "/user-members/" + id;
        restTemplate.delete(url);
    }
}