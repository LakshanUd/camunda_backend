package com.example.camunda_backend.controller;

import com.example.camunda_backend.dto.UserCreateRequest;
import com.example.camunda_backend.dto.UserDto;
import com.example.camunda_backend.dto.UserUpdateRequest;
import com.example.camunda_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 1. Get all users from MySQL
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 2. Get single user details
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id or #id == authentication.principal.username")
    public ResponseEntity<UserDto> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // 3. Create a new user in MySQL
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // 4. Update an existing user
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> updateUser(@PathVariable String id, @Valid @RequestBody UserUpdateRequest request) {
        UserDto updated = userService.updateUser(id, request);
        return ResponseEntity.ok(updated);
    }

    // 5. Enable or disable user status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleUserStatus(@PathVariable String id, @RequestBody Map<String, Boolean> payload) {
        boolean active = payload.getOrDefault("active", true);
        try {
            userService.toggleUserStatus(id, active);
            return ResponseEntity.ok(Map.of(
                    "message", "User status updated successfully",
                    "id", id,
                    "active", active
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 6. Delete user from MySQL
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully", "id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 7. Profile endpoints (accessible by self or admin)
    @GetMapping("/{id}/profile")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id or #id == authentication.principal.username")
    public ResponseEntity<?> getProfile(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id or #id == authentication.principal.username")
    public ResponseEntity<?> updateProfile(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        try {
            userService.updateUserProfile(id, payload);
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/credentials")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id or #id == authentication.principal.username")
    public ResponseEntity<?> updateCredentials(@PathVariable String id, @RequestBody Map<String, String> payload) {
        try {
            userService.updateUserCredentials(id, payload);
            return ResponseEntity.ok(Map.of("message", "Credentials updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}