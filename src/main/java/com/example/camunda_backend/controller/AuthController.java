package com.example.camunda_backend.controller;

import com.example.camunda_backend.dto.AuthResponse;
import com.example.camunda_backend.dto.LoginRequest;
import com.example.camunda_backend.dto.RegisterRequest;
import com.example.camunda_backend.entity.Role;
import com.example.camunda_backend.entity.User;
import com.example.camunda_backend.repository.RoleRepository;
import com.example.camunda_backend.repository.UserRepository;
import com.example.camunda_backend.security.CustomUserPrincipal;
import com.example.camunda_backend.security.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Authenticate credentials against custom MySQL app_users table
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();

        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        boolean isAdmin = roles.contains("ROLE_ADMIN") || roles.contains("ADMIN");

        String jwt = jwtUtils.generateToken(
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                userPrincipal.getEmail(),
                userPrincipal.getFullName(),
                roles
        );

        AuthResponse response = new AuthResponse(
                jwt,
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                userPrincipal.getEmail(),
                userPrincipal.getFullName(),
                roles,
                isAdmin
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // 1. Validation checks
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken."));
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use."));
        }

        // 2. Create user entity
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setActive(true);

        // 3. Assign default role ROLE_WORKER for public self-registration (prevent privilege escalation)
        Set<Role> roles = new HashSet<>();
        Role workerRole = roleRepository.findByRoleName("ROLE_WORKER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_WORKER", "Standard workflow task worker")));
        roles.add(workerRole);
        user.setRoles(roles);

        // 4. Save to custom MySQL database
        User savedUser = userRepository.save(user);

        List<String> roleNames = savedUser.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());

        boolean isAdmin = roleNames.contains("ROLE_ADMIN");

        String jwt = jwtUtils.generateToken(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                roleNames
        );

        AuthResponse response = new AuthResponse(
                jwt,
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                roleNames,
                isAdmin
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        boolean isAdmin = roles.contains("ROLE_ADMIN") || roles.contains("ADMIN");

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", principal.getId());
        userData.put("username", principal.getUsername());
        userData.put("email", principal.getEmail());
        userData.put("fullName", principal.getFullName());
        userData.put("roles", roles);
        userData.put("isAdmin", isAdmin);

        return ResponseEntity.ok(userData);
    }
}