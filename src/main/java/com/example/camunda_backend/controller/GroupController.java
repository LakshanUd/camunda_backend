package com.example.camunda_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Value("${camunda.api.url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 1. Get all groups
    @GetMapping
    public Object getAllGroups() {
        String url = camundaUrl + "/group";
        return restTemplate.getForObject(url, Object.class);
    }

    // 2. Create a new group
    @PostMapping("/create")
    public Object createGroup(@RequestBody Map<String, String> groupData) {
        String url = camundaUrl + "/group/create";
        // Camunda expects: { "id": "...", "name": "...", "type": "..." }
        return restTemplate.postForObject(url, groupData, Object.class);
    }

    // 3. Get a single group by ID
    @GetMapping("/{id}")
    public Object getGroup(@PathVariable String id) {
        String url = camundaUrl + "/group/" + id;
        return restTemplate.getForObject(url, Object.class);
    }

    // 4. Update an existing group
    @PutMapping("/{id}")
    public void updateGroup(@PathVariable String id, @RequestBody Map<String, String> groupData) {
        String url = camundaUrl + "/group/" + id;
        // Camunda requires the ID in the URL and the full object in the body
        restTemplate.put(url, groupData);
    }

    // 5. Delete a group
    @DeleteMapping("/{id}")
    public void deleteGroup(@PathVariable String id) {
        String url = camundaUrl + "/group/" + id;
        restTemplate.delete(url);
    }
}