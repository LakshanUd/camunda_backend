package com.example.camunda_backend.service;

import com.example.camunda_backend.entity.BusinessDataField;
import com.example.camunda_backend.repository.BusinessDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class BusinessDataService {

    private final BusinessDataRepository businessDataRepository;

    // Define standard routing keys that MUST be sent to Camunda's engine
    private static final Set<String> ENGINE_ROUTING_KEYS = Set.of(
        "nextReviewer", "decision", "approved", "assignee", "candidateGroup", "status"
    );

    public BusinessDataService(BusinessDataRepository businessDataRepository) {
        this.businessDataRepository = businessDataRepository;
    }

    // 1. TRANSACTIONAL SAVE: Strips out business data and saves it securely to MySQL
    @Transactional
    public Map<String, Object> extractAndSaveBusinessData(String processInstanceId, String taskId, String userId, Map<String, Object> rawVariables) {
        Map<String, Object> enginePayload = new HashMap<>();

        if (rawVariables == null || rawVariables.isEmpty()) {
            return enginePayload;
        }

        for (Map.Entry<String, Object> entry : rawVariables.entrySet()) {
            String key = entry.getKey();
            Object varObj = entry.getValue();

            // Extract value and type from standard Camunda variable format: {"value": X, "type": "String"}
            String valStr = "";
            String typeStr = "String";
            if (varObj instanceof Map) {
                Map<?, ?> varMap = (Map<?, ?>) varObj;
                valStr = varMap.get("value") != null ? varMap.get("value").toString() : "";
                typeStr = varMap.get("type") != null ? varMap.get("type").toString() : "String";
            } else if (varObj != null) {
                valStr = varObj.toString();
            }

            // If it's a routing variable, keep it in the payload destined for Camunda!
            if (ENGINE_ROUTING_KEYS.contains(key)) {
                enginePayload.put(key, varObj);
            } else {
                // Otherwise, it is custom Business Data -> Save or update in MySQL table!
                Optional<BusinessDataField> existingOpt = businessDataRepository.findByProcessInstanceIdAndFieldKey(processInstanceId, key);
                BusinessDataField field = existingOpt.orElse(new BusinessDataField());
                
                field.setProcessInstanceId(processInstanceId != null ? processInstanceId : "UNKNOWN");
                field.setTaskId(taskId);
                field.setFieldKey(key);
                field.setFieldValue(valStr);
                field.setFieldType(typeStr);
                field.setUpdatedBy(userId != null ? userId : "ANONYMOUS");

                businessDataRepository.save(field);
                System.out.println("💾 [MYSQL BUSINESS DATA] Saved: [" + key + "] = '" + valStr + "' (" + typeStr + ") for Process: " + processInstanceId);
            }
        }

        return enginePayload; // Returns ONLY the lightweight routing variables for Camunda!
    }

    // 2. MERGE DATA: Fetches MySQL business fields and formats them to match Camunda's UI schema
    public Map<String, Object> getMergedBusinessData(String processInstanceId) {
        Map<String, Object> mergedData = new HashMap<>();
        if (processInstanceId == null) return mergedData;

        List<BusinessDataField> fields = businessDataRepository.findByProcessInstanceId(processInstanceId);
        for (BusinessDataField f : fields) {
            // Format back to Camunda variable structure so Angular UI renders it seamlessly:
            mergedData.put(f.getFieldKey(), Map.of(
                "value", f.getFieldValue() != null ? f.getFieldValue() : "",
                "type", f.getFieldType() != null ? f.getFieldType() : "String"
            ));
        }
        return mergedData;
    }
}