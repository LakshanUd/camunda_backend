package com.example.camunda_backend.controller;

import com.example.camunda_backend.entity.BusinessDataField;
import com.example.camunda_backend.repository.BusinessDataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final BusinessDataRepository businessDataRepository;

    public ReportController(BusinessDataRepository businessDataRepository) {
        this.businessDataRepository = businessDataRepository;
    }

    @GetMapping("/business-ledger")
    public ResponseEntity<List<Map<String, Object>>> getBusinessLedger() {
        // Fetch ALL custom business data from MySQL
        List<BusinessDataField> allFields = businessDataRepository.findAll();

        // Group the fields by their Process Instance ID so we can flatten them into a single row per process
        Map<String, List<BusinessDataField>> groupedByProcess = allFields.stream()
                .collect(Collectors.groupingBy(BusinessDataField::getProcessInstanceId));

        List<Map<String, Object>> flatLedger = new ArrayList<>();

        for (Map.Entry<String, List<BusinessDataField>> entry : groupedByProcess.entrySet()) {
            String processId = entry.getKey();
            List<BusinessDataField> fields = entry.getValue();

            Map<String, Object> row = new HashMap<>();
            row.put("Process ID", processId);
            
            // Get the latest update time from the fields for this process
            String lastUpdated = fields.stream()
                .map(f -> f.getUpdatedAt().toString())
                .max(String::compareTo).orElse("Unknown");
            row.put("Last Updated", lastUpdated);

            // Pivot the EAV rows into dynamic columns (e.g., customerName -> "Lakshan")
            for (BusinessDataField field : fields) {
                row.put(field.getFieldKey(), field.getFieldValue());
            }

            flatLedger.add(row);
        }

        return ResponseEntity.ok(flatLedger);
    }
}