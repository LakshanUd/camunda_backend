package com.example.camunda_backend.service;

import com.example.camunda_backend.entity.CustomFormSubmission;
import com.example.camunda_backend.entity.CustomFormVariable;
import com.example.camunda_backend.entity.CustomProcessInstance;
import com.example.camunda_backend.entity.CustomTaskRecord;
import com.example.camunda_backend.repository.CustomFormSubmissionRepository;
import com.example.camunda_backend.repository.CustomFormVariableRepository;
import com.example.camunda_backend.repository.CustomProcessInstanceRepository;
import com.example.camunda_backend.repository.CustomTaskRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FormAuditService {

    private static final Logger log = LoggerFactory.getLogger(FormAuditService.class);

    private final CustomProcessInstanceRepository processInstanceRepo;
    private final CustomTaskRecordRepository taskRecordRepo;
    private final CustomFormSubmissionRepository formSubmissionRepo;
    private final CustomFormVariableRepository formVariableRepo;
    private final ObjectMapper objectMapper;

    public FormAuditService(CustomProcessInstanceRepository processInstanceRepo,
                            CustomTaskRecordRepository taskRecordRepo,
                            CustomFormSubmissionRepository formSubmissionRepo,
                            CustomFormVariableRepository formVariableRepo,
                            ObjectMapper objectMapper) {
        this.processInstanceRepo = processInstanceRepo;
        this.taskRecordRepo = taskRecordRepo;
        this.formSubmissionRepo = formSubmissionRepo;
        this.formVariableRepo = formVariableRepo;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    // =========================================================================
    // PROCESS INSTANCES PERSISTENCE
    // =========================================================================

    @Transactional
    public void recordProcessInstanceStart(String processInstanceId,
                                           String processDefinitionKey,
                                           String processDefinitionName,
                                           Integer version,
                                           String businessKey,
                                           String startUserId) {
        if (processInstanceId == null || processInstanceId.isBlank()) return;

        try {
            CustomProcessInstance instance = processInstanceRepo.findById(processInstanceId)
                    .orElseGet(() -> new CustomProcessInstance(
                            processInstanceId,
                            processDefinitionKey,
                            processDefinitionName,
                            version,
                            businessKey,
                            startUserId,
                            LocalDateTime.now(),
                            "ACTIVE"
                    ));

            instance.setStatus("ACTIVE");
            if (processDefinitionKey != null) instance.setProcessDefinitionKey(processDefinitionKey);
            if (processDefinitionName != null) instance.setProcessDefinitionName(processDefinitionName);
            if (version != null) instance.setProcessDefinitionVersion(version);
            if (startUserId != null) instance.setStartUserId(startUserId);

            processInstanceRepo.save(instance);
            log.info("[AUDIT-DB] Process instance '{}' recorded in database (status: ACTIVE, starter: '{}')",
                    processInstanceId, startUserId);
        } catch (Exception e) {
            log.error("[AUDIT-DB] Failed to record process instance start for '{}': {}", processInstanceId, e.getMessage());
        }
    }

    @Transactional
    public void recordProcessInstanceCompletion(String processInstanceId, LocalDateTime endTime, Long durationMs, String status) {
        if (processInstanceId == null) return;
        try {
            processInstanceRepo.findById(processInstanceId).ifPresent(inst -> {
                inst.setEndTime(endTime != null ? endTime : LocalDateTime.now());
                if (durationMs != null) {
                    inst.setDurationMs(durationMs);
                } else if (inst.getStartTime() != null) {
                    inst.setDurationMs(Duration.between(inst.getStartTime(), inst.getEndTime()).toMillis());
                }
                inst.setStatus(status != null ? status : "COMPLETED");
                processInstanceRepo.save(inst);
                log.info("[AUDIT-DB] Process instance '{}' marked COMPLETED in database", processInstanceId);
            });
        } catch (Exception e) {
            log.error("[AUDIT-DB] Failed to record process instance completion for '{}': {}", processInstanceId, e.getMessage());
        }
    }

    // =========================================================================
    // TASK LIFECYCLE PERSISTENCE
    // =========================================================================

    @Transactional
    public void recordTaskCreatedOrUpdated(Map<String, Object> taskMap) {
        if (taskMap == null) return;
        String taskId = (String) taskMap.get("id");
        if (taskId == null || taskId.isBlank()) return;

        try {
            String name = (String) taskMap.get("name");
            String taskDefKey = (String) taskMap.get("taskDefinitionKey");
            String procInstId = (String) taskMap.get("processInstanceId");
            String procDefId = (String) taskMap.get("processDefinitionId");
            String procDefKey = procDefId != null && procDefId.contains(":") ? procDefId.split(":")[0] : null;
            String procDefName = (String) taskMap.get("processDefinitionName");
            String assignee = (String) taskMap.get("assignee");
            String candidateGroup = (String) taskMap.get("candidateGroup");

            CustomTaskRecord record = taskRecordRepo.findById(taskId).orElseGet(() -> {
                CustomTaskRecord r = new CustomTaskRecord();
                r.setId(taskId);
                r.setStartTime(LocalDateTime.now());
                return r;
            });

            if (name != null) record.setName(name);
            if (taskDefKey != null) record.setTaskDefinitionKey(taskDefKey);
            if (procInstId != null) record.setProcessInstanceId(procInstId);
            if (procDefKey != null) record.setProcessDefinitionKey(procDefKey);
            if (procDefName != null) record.setProcessDefinitionName(procDefName);
            record.setAssignee(assignee);
            if (candidateGroup != null) record.setCandidateGroup(candidateGroup);

            // Determine status
            if (record.getCompletedTime() != null) {
                record.setStatus("COMPLETED");
            } else if (assignee != null && !assignee.isBlank()) {
                record.setStatus("CLAIMED");
            } else {
                record.setStatus("ACTIVE");
            }

            taskRecordRepo.save(record);
        } catch (Exception e) {
            log.debug("[AUDIT-DB] Failed to record task update for '{}': {}", taskId, e.getMessage());
        }
    }

    @Transactional
    public void recordTaskClaimed(String taskId, String username) {
        if (taskId == null || username == null) return;
        try {
            CustomTaskRecord record = taskRecordRepo.findById(taskId).orElseGet(() -> {
                CustomTaskRecord r = new CustomTaskRecord();
                r.setId(taskId);
                r.setStartTime(LocalDateTime.now());
                return r;
            });
            record.setAssignee(username);
            record.setStatus("CLAIMED");
            taskRecordRepo.save(record);
            log.info("[AUDIT-DB] Task '{}' marked CLAIMED by user '{}' in database", taskId, username);
        } catch (Exception e) {
            log.error("[AUDIT-DB] Failed to record task claim for '{}': {}", taskId, e.getMessage());
        }
    }

    @Transactional
    public void recordTaskUnclaimed(String taskId, String fallbackGroupId) {
        if (taskId == null) return;
        try {
            taskRecordRepo.findById(taskId).ifPresent(record -> {
                record.setAssignee(null);
                record.setStatus("UNCLAIMED");
                if (fallbackGroupId != null && !fallbackGroupId.isBlank()) {
                    record.setCandidateGroup(fallbackGroupId);
                }
                taskRecordRepo.save(record);
                log.info("[AUDIT-DB] Task '{}' marked UNCLAIMED in database", taskId);
            });
        } catch (Exception e) {
            log.error("[AUDIT-DB] Failed to record task unclaim for '{}': {}", taskId, e.getMessage());
        }
    }

    // =========================================================================
    // FORM SUBMISSION & VARIABLES PERSISTENCE
    // =========================================================================

    @Transactional
    @SuppressWarnings("unchecked")
    public CustomFormSubmission recordFormSubmission(String taskId,
                                                    String submittedBy,
                                                    Map<String, Object> submittedPayload,
                                                    Object formSchemaObj,
                                                    String formKey) {
        if (taskId == null) return null;

        try {
            // 1. Resolve Task Details
            CustomTaskRecord taskRecord = taskRecordRepo.findById(taskId).orElse(null);
            String procInstId = taskRecord != null ? taskRecord.getProcessInstanceId() : null;
            String taskDefKey = taskRecord != null ? taskRecord.getTaskDefinitionKey() : null;
            String taskName = taskRecord != null ? taskRecord.getName() : null;

            // 2. Extract values map from payload (either { variables: { field: { value: ... } } } or direct map)
            Map<String, Object> extractedValues = new LinkedHashMap<>();
            Map<String, String> extractedTypes = new HashMap<>();

            if (submittedPayload != null) {
                if (submittedPayload.containsKey("variables") && submittedPayload.get("variables") instanceof Map) {
                    Map<String, Object> vars = (Map<String, Object>) submittedPayload.get("variables");
                    for (Map.Entry<String, Object> entry : vars.entrySet()) {
                        String fieldName = entry.getKey();
                        Object fieldObj = entry.getValue();
                        if (fieldObj instanceof Map) {
                            Map<String, Object> fieldMap = (Map<String, Object>) fieldObj;
                            extractedValues.put(fieldName, fieldMap.get("value"));
                            if (fieldMap.containsKey("type")) {
                                extractedTypes.put(fieldName, String.valueOf(fieldMap.get("type")));
                            }
                        } else {
                            extractedValues.put(fieldName, fieldObj);
                        }
                    }
                } else {
                    extractedValues.putAll(submittedPayload);
                }
            }

            // 3. Serialize Data and Schema JSON
            String formDataJson = objectMapper.writeValueAsString(extractedValues);
            String formSchemaJson = null;
            if (formSchemaObj != null) {
                if (formSchemaObj instanceof String) {
                    formSchemaJson = (String) formSchemaObj;
                } else {
                    formSchemaJson = objectMapper.writeValueAsString(formSchemaObj);
                }
            }

            // 4. Save CustomFormSubmission
            CustomFormSubmission submission = new CustomFormSubmission(
                    taskId,
                    procInstId,
                    taskDefKey,
                    taskName,
                    formKey,
                    submittedBy,
                    formDataJson,
                    formSchemaJson
            );
            submission = formSubmissionRepo.save(submission);

            // 5. Save Individual CustomFormVariable rows
            List<CustomFormVariable> varEntities = new ArrayList<>();
            for (Map.Entry<String, Object> entry : extractedValues.entrySet()) {
                String varName = entry.getKey();
                Object varVal = entry.getValue();
                String varType = extractedTypes.getOrDefault(varName, resolveType(varVal));
                String varValStr = (varVal != null) ? (varVal instanceof String ? (String) varVal : objectMapper.writeValueAsString(varVal)) : null;

                varEntities.add(new CustomFormVariable(
                        submission.getId(),
                        taskId,
                        procInstId,
                        varName,
                        varType,
                        varValStr
                ));
            }
            if (!varEntities.isEmpty()) {
                formVariableRepo.saveAll(varEntities);
            }

            // 6. Update CustomTaskRecord as COMPLETED
            LocalDateTime now = LocalDateTime.now();
            if (taskRecord == null) {
                taskRecord = new CustomTaskRecord();
                taskRecord.setId(taskId);
                taskRecord.setStartTime(now);
                taskRecord.setProcessInstanceId(procInstId);
            }
            taskRecord.setStatus("COMPLETED");
            taskRecord.setCompletedBy(submittedBy);
            taskRecord.setCompletedTime(now);
            if (taskRecord.getStartTime() != null) {
                taskRecord.setDurationMs(Duration.between(taskRecord.getStartTime(), now).toMillis());
            }
            taskRecordRepo.save(taskRecord);

            log.info("[AUDIT-DB] Successfully saved form submission for task '{}' (by: '{}', fields: {})",
                    taskId, submittedBy, extractedValues.size());

            return submission;

        } catch (Exception e) {
            log.error("[AUDIT-DB] Error saving form submission for task '{}': {}", taskId, e.getMessage(), e);
            return null;
        }
    }

    private String resolveType(Object val) {
        if (val == null) return "String";
        if (val instanceof Boolean) return "Boolean";
        if (val instanceof Integer || val instanceof Long || val instanceof Short) return "Long";
        if (val instanceof Double || val instanceof Float) return "Double";
        if (val instanceof Date) return "Date";
        if (val instanceof Map || val instanceof List) return "Json";
        return "String";
    }

    // =========================================================================
    // QUERY METHODS (LOCAL DATABASE FIRST)
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<CustomFormSubmission> getFormSubmissionForTask(String taskId) {
        if (taskId == null) return Optional.empty();
        return formSubmissionRepo.findFirstByTaskIdOrderBySubmittedAtDesc(taskId);
    }

    @Transactional(readOnly = true)
    public List<CustomFormVariable> getVariablesForTask(String taskId) {
        if (taskId == null) return Collections.emptyList();
        return formVariableRepo.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public List<CustomFormSubmission> getSubmissionsForProcessInstance(String processInstanceId) {
        if (processInstanceId == null) return Collections.emptyList();
        return formSubmissionRepo.findByProcessInstanceIdOrderBySubmittedAtAsc(processInstanceId);
    }
}
