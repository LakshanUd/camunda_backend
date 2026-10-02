package com.example.camunda_backend.config;

import com.example.camunda_backend.entity.Role;
import com.example.camunda_backend.entity.User;
import com.example.camunda_backend.repository.RoleRepository;
import com.example.camunda_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner seedDatabase(RoleRepository roleRepository,
                                         UserRepository userRepository,
                                         PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Ensure Roles exist
            Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN", "Global System Administrator")));

            Role workerRole = roleRepository.findByRoleName("ROLE_WORKER")
                    .orElseGet(() -> roleRepository.save(new Role("ROLE_WORKER", "Standard workflow task worker")));

            // 2. Ensure Default Administrator exists
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setId(UUID.randomUUID().toString());
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("Admin@1234"));
                admin.setEmail("admin@camunda-app.local");
                admin.setFullName("System Administrator");
                admin.setActive(true);
                admin.setCreatedAt(LocalDateTime.now());
                admin.setRoles(Set.of(adminRole));

                userRepository.save(admin);
                logger.info(">>> Seeded default ADMIN user: username='admin', password='Admin@1234'");
            }

            // 3. Ensure Default Worker exists
            if (!userRepository.existsByUsername("worker")) {
                User worker = new User();
                worker.setId(UUID.randomUUID().toString());
                worker.setUsername("worker");
                worker.setPasswordHash(passwordEncoder.encode("Worker@1234"));
                worker.setEmail("worker@camunda-app.local");
                worker.setFullName("Standard Task Worker");
                worker.setActive(true);
                worker.setCreatedAt(LocalDateTime.now());
                worker.setRoles(Set.of(workerRole));

                userRepository.save(worker);
                logger.info(">>> Seeded default WORKER user: username='worker', password='Worker@1234'");
            }
        };
    }
}
