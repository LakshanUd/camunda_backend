package com.example.camunda_backend.service;

import com.example.camunda_backend.dto.UserCreateRequest;
import com.example.camunda_backend.dto.UserDto;
import com.example.camunda_backend.dto.UserUpdateRequest;
import com.example.camunda_backend.entity.Role;
import com.example.camunda_backend.entity.User;
import com.example.camunda_backend.repository.RoleRepository;
import com.example.camunda_backend.repository.UserRepository;
import com.example.camunda_backend.repository.UserWorkflowAssignmentRepository;
import com.example.camunda_backend.security.CustomUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserWorkflowAssignmentRepository workflowAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserWorkflowAssignmentRepository workflowAssignmentRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.workflowAssignmentRepository = workflowAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        return mapToDto(user);
    }

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already in use.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setActive(request.isActive());

        Set<Role> roles = resolveRoles(request.getRoles());
        user.setRoles(roles);

        User saved = userRepository.save(user);
        return mapToDto(saved);
    }

    @Transactional
    public UserDto updateUser(String id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));

        // Check if email changed and is taken by another user
        if (!user.getEmail().equalsIgnoreCase(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already in use.");
        }

        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> roles = resolveRoles(request.getRoles());
            user.setRoles(roles);
        }

        User updated = userRepository.save(user);
        return mapToDto(updated);
    }

    @Transactional
    public void toggleUserStatus(String id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));

        if (!active) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal currentPrincipal) {
                if (id.equals(currentPrincipal.getId()) || user.getUsername().equals(currentPrincipal.getUsername())) {
                    throw new IllegalStateException("You cannot disable your own account.");
                }
            }

            boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()) || "ADMIN".equals(r.getRoleName()));
            if (isAdmin) {
                long activeAdminCount = userRepository.findAll().stream()
                        .filter(User::isActive)
                        .filter(u -> u.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()) || "ADMIN".equals(r.getRoleName())))
                        .count();
                if (activeAdminCount <= 1) {
                    throw new IllegalStateException("Cannot disable the last active administrator.");
                }
            }
        }

        user.setActive(active);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal currentPrincipal) {
            if (id.equals(currentPrincipal.getId()) || user.getUsername().equals(currentPrincipal.getUsername())) {
                throw new IllegalStateException("You cannot delete your own account.");
            }
        }

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()) || "ADMIN".equals(r.getRoleName()));
        if (isAdmin) {
            long activeAdminCount = userRepository.findAll().stream()
                    .filter(User::isActive)
                    .filter(u -> u.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()) || "ADMIN".equals(r.getRoleName())))
                    .count();
            if (activeAdminCount <= 1) {
                throw new IllegalStateException("Cannot delete the last active administrator.");
            }
        }

        // Clean up workflow assignments for this user (handles assignments mapped by UUID or username)
        workflowAssignmentRepository.deleteByUserId(user.getId());
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            workflowAssignmentRepository.deleteByUserId(user.getUsername());
        }
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserProfile(String id) {
        User user = userRepository.findById(id)
                .orElseGet(() -> userRepository.findByUsername(id)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + id)));

        String fullName = user.getFullName() != null ? user.getFullName() : "";
        String firstName = "";
        String lastName = "";
        if (!fullName.isBlank()) {
            String[] parts = fullName.split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("fullName", fullName);
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("active", user.isActive());
        profile.put("roles", user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()));
        return profile;
    }

    @Transactional
    public void updateUserProfile(String id, Map<String, Object> payload) {
        User user = userRepository.findById(id)
                .orElseGet(() -> userRepository.findByUsername(id)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + id)));

        String email = (String) payload.get("email");
        if (email != null && !email.isBlank()) {
            if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email is already in use.");
            }
            user.setEmail(email);
        }

        String firstName = (String) payload.get("firstName");
        String lastName = (String) payload.get("lastName");
        String fullName = (String) payload.get("fullName");

        if (firstName != null || lastName != null) {
            String combined = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            user.setFullName(combined);
        } else if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }

        userRepository.save(user);
    }

    @Transactional
    public void updateUserCredentials(String id, Map<String, String> payload) {
        User user = userRepository.findById(id)
                .orElseGet(() -> userRepository.findByUsername(id)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + id)));

        String currentPassword = payload.get("authenticatedUserPassword");
        String newPassword = payload.get("password");

        if (currentPassword == null || currentPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Both current and new passwords are required.");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String name : roleNames) {
                String normalized = name.startsWith("ROLE_") ? name : "ROLE_" + name;
                Role role = roleRepository.findByRoleName(normalized)
                        .orElseGet(() -> roleRepository.save(new Role(normalized, "User role")));
                roles.add(role);
            }
        } else {
            Role defaultRole = roleRepository.findByRoleName("ROLE_WORKER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_WORKER", "Standard worker role")));
            roles.add(defaultRole);
        }
        return roles;
    }

    public UserDto mapToDto(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());

        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.isActive(),
                roleNames,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
