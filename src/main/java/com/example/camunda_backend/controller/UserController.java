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
}