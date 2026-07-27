package com.example.camunda_backend.service;

import com.example.camunda_backend.entity.ProcessMirror;
import com.example.camunda_backend.entity.TaskMirror;
import com.example.camunda_backend.repository.ProcessMirrorRepository;
import com.example.camunda_backend.repository.TaskMirrorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TaskSyncService {

    private final TaskMirrorRepository taskMirrorRepository;
    private final ProcessMirrorRepository processMirrorRepository;

    public TaskSyncService(TaskMirrorRepository taskMirrorRepository, ProcessMirrorRepository processMirrorRepository) {
        this.taskMirrorRepository = taskMirrorRepository;
        this.processMirrorRepository = processMirrorRepository;
    }

    // 1. SYNC TASK CREATION / UPDATE: Saves or updates an active task in MySQL
    @Transactional
    public void syncTask(String taskId, String processInstanceId, String taskName, 
                         String taskDefKey, String assignee, String candidateGroup, String status) {
        
        TaskMirror task = taskMirrorRepository.findById(taskId).orElse(new TaskMirror());
        task.setTaskId(taskId);
        task.setProcessInstanceId(processInstanceId != null ? processInstanceId : "UNKNOWN_PROCESS");
        task.setTaskName(taskName != null ? taskName : "Unnamed Task");
        task.setTaskDefinitionKey(taskDefKey);
        task.setAssignee(assignee);
        task.setCandidateGroup(candidateGroup);
        task.setStatus(status != null ? status : "ACTIVE");
        
        if (task.getCreateTime() == null) {
            task.setCreateTime(LocalDateTime.now());
        }
        
        taskMirrorRepository.save(task);
        System.out.println("🔄 [MYSQL SYNC] Task Mirrored: [" + taskId + "] -> Status: " + task.getStatus() + " | Assignee: " + assignee);
    }

    // 2. SYNC TASK COMPLETION: Marks a task as COMPLETED in MySQL and records decisions
    @Transactional
    public void syncTaskCompletion(String taskId, String decision, String comment) {
        Optional<TaskMirror> taskOpt = taskMirrorRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            TaskMirror task = taskOpt.get();
            task.setStatus("COMPLETED");
            task.setEndTime(LocalDateTime.now());
            task.setCompletionDecision(decision != null ? decision : "Completed");
            task.setCompletionComment(comment);
            taskMirrorRepository.save(task);
            System.out.println("✅ [MYSQL SYNC] Task Completed in MySQL: [" + taskId + "] | Decision: " + task.getCompletionDecision());
        } else {
            // If completed before daemon caught it, create a historical record anyway
            TaskMirror task = new TaskMirror();
            task.setTaskId(taskId);
            task.setProcessInstanceId("UNKNOWN");
            task.setTaskName("Completed Task");
            task.setStatus("COMPLETED");
            task.setCreateTime(LocalDateTime.now());
            task.setEndTime(LocalDateTime.now());
            task.setCompletionDecision(decision);
            task.setCompletionComment(comment);
            taskMirrorRepository.save(task);
        }
    }

    // 3. SYNC PROCESS INSTANCE: Saves or updates workflow level status
    @Transactional
    public void syncProcessInstance(String processInstanceId, String processDefKey, String businessKey, String startUserId, String status) {
        ProcessMirror process = processMirrorRepository.findById(processInstanceId).orElse(new ProcessMirror());
        process.setProcessInstanceId(processInstanceId);
        process.setProcessDefinitionKey(processDefKey != null ? processDefKey : "UNKNOWN_DEF");
        process.setBusinessKey(businessKey);
        process.setStartUserId(startUserId);
        process.setStatus(status != null ? status : "ACTIVE");
        
        if (process.getStartTime() == null) {
            process.setStartTime(LocalDateTime.now());
        }
        if ("COMPLETED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            process.setEndTime(LocalDateTime.now());
        }
        processMirrorRepository.save(process);
    }
}