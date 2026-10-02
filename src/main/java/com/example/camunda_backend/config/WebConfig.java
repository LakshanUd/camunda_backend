package com.example.camunda_backend.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {
    // CORS configuration is centralized in SecurityConfig via CorsConfigurationSource
    // to ensure Spring Security filters and Spring MVC apply uniform policies.
}