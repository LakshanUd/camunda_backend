package com.example.camunda_backend.controller;

import com.example.camunda_backend.service.TaskInterceptorService;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/groups")
public class GroupController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private TaskInterceptorService taskInterceptorService;

    // 1. Get all groups
    @GetMapping
    public ResponseEntity<?> getAllGroups() {
        try {
            String url = camundaUrl + "/group";
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response != null ? response : Collections.emptyList());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. Create a new group
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createGroup(@RequestBody Map<String, String> groupData) {
        try {
            String url = camundaUrl + "/group/create";
            Object response = restTemplate.postForObject(url, groupData, Object.class);
            return ResponseEntity.ok(response != null ? response : Map.of("status", "Group created successfully"));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. Get a single group by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(@PathVariable String id) {
        try {
            String url = camundaUrl + "/group/" + id;
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. Update an existing group
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateGroup(@PathVariable String id, @RequestBody Map<String, String> groupData) {
        try {
            String url = camundaUrl + "/group/" + id;
            restTemplate.put(url, groupData);
            return ResponseEntity.ok(Map.of("status", "Group updated successfully", "id", id));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 5. Delete a group
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteGroup(@PathVariable String id) {
        try {
            String url = camundaUrl + "/group/" + id;
            restTemplate.delete(url);
            return ResponseEntity.ok(Map.of("status", "Group deleted successfully", "id", id));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 6. Get members of a group
    @GetMapping("/{id}/members")
    public ResponseEntity<?> getGroupMembers(@PathVariable String id) {
        try {
            return ResponseEntity.ok(taskInterceptorService.getGroupMembers(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 7. Add user to group
    @PostMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addUserToGroup(@PathVariable String id, @PathVariable String userId) {
        try {
            taskInterceptorService.addUserToGroup(id, userId);
            return ResponseEntity.ok(Map.of("status", "User added to group successfully", "groupId", id, "userId", userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 8. Remove user from group
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removeUserFromGroup(@PathVariable String id, @PathVariable String userId) {
        try {
            taskInterceptorService.removeUserFromGroup(id, userId);
            return ResponseEntity.ok(Map.of("status", "User removed from group successfully", "groupId", id, "userId", userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // 9. Get groups for a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserGroups(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(taskInterceptorService.getUserGroups(userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}