package com.example.camunda_backend.controller;

/**
 * =========================================================================
 * DEPRECATED — SUPERSEDED BY AssignmentController.java
 * =========================================================================
 *
 * This class has been replaced by AssignmentController.java as part of
 * the Workflow Assignment Engine rebuild (Phase 2 of the full rewrite).
 *
 * DO NOT re-enable this class. It has been emptied to avoid Spring Boot
 * startup failures caused by duplicate @RequestMapping("/api/assignments")
 * registrations.
 *
 * Old capabilities (now rebuilt in AssignmentController.java):
 *   - User-to-workflow authorizations   → /api/assignments/workflow/{key}/authorizations/user
 *   - Group-to-workflow authorizations  → /api/assignments/workflow/{key}/authorizations/group
 *   - Task routing rules                → /api/assignments/workflow/{key}/tasks/{id}/rules
 *   - Available workflows proxy         → /api/assignments/available-workflows
 *   - BPMN task extraction              → /api/assignments/available-workflows/{id}/tasks
 *
 * SAFE TO DELETE this file entirely.
 * =========================================================================
 */
public final class WorkflowAssignmentController {
    // intentionally empty — do not instantiate
    private WorkflowAssignmentController() {}
}
