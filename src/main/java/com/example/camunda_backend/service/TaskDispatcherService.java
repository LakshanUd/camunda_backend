package com.example.camunda_backend.service;

/**
 * =========================================================================
 * DEPRECATED — SUPERSEDED BY TaskInterceptorService.java
 * =========================================================================
 *
 * This class has been REPLACED by TaskInterceptorService.java as part of
 * the Workflow Assignment Engine rebuild.
 *
 * ROOT CAUSE OF BUG: This class used REST-polling hooks (called on every
 * GET /tasks/* request and on process start) to assign tasks. This caused:
 *   1. Tasks to be assigned immediately on process start (before the admin
 *      could even configure routing rules).
 *   2. Race conditions from concurrent REST calls during page loads.
 *   3. Double-assignment when multiple users loaded the task list at once.
 *
 * The replacement (TaskInterceptorService) uses a Camunda @EventListener
 * targeting ONLY task.create events — zero polling, zero race conditions.
 *
 * SAFE TO DELETE this file entirely.
 * =========================================================================
 */
public final class TaskDispatcherService {
    // intentionally empty — do not instantiate
    private TaskDispatcherService() {}
}
