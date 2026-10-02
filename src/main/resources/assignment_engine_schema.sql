-- ============================================================
-- WORKFLOW ASSIGNMENT ENGINE — CUSTOM MYSQL SCHEMA
-- Run this script AFTER your Spring Boot app creates the base
-- tables via ddl-auto=update, OR apply it manually in MySQL.
-- ============================================================

-- -----------------------------------------------------------
-- STEP 1: DROP OLD BUGGY TABLES (if they exist)
-- -----------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `task_assignment_rules`;
DROP TABLE IF EXISTS `user_workflow_assignments`;

-- -----------------------------------------------------------
-- STEP 2: CREATE NEW ENGINE TABLES
-- -----------------------------------------------------------

-- Table 1: app_groups
-- Stores custom identity groups managed entirely by this app
CREATE TABLE IF NOT EXISTS `app_groups` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL UNIQUE COMMENT 'Unique, human-readable group name (e.g., finance-team)',
    `description` VARCHAR(255)          COMMENT 'Optional description of the group purpose',
    `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_app_groups_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 2: user_group_mapping
-- Many-to-many junction: maps app_users to app_groups
CREATE TABLE IF NOT EXISTS `user_group_mapping` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    VARCHAR(36) NOT NULL COMMENT 'UUID of the user from app_users.id',
    `group_id`   BIGINT      NOT NULL COMMENT 'FK to app_groups.id',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_group` (`user_id`, `group_id`),
    INDEX `idx_ugm_user` (`user_id`),
    INDEX `idx_ugm_group` (`group_id`),
    CONSTRAINT `fk_ugm_group` FOREIGN KEY (`group_id`) REFERENCES `app_groups` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 3: workflow_authorizations
-- Defines WHO is authorized to start (or be assigned to) a given workflow.
-- Either user_id OR group_id must be set, not both.
CREATE TABLE IF NOT EXISTS `workflow_authorizations` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `workflow_key` VARCHAR(100) NOT NULL COMMENT 'Camunda process definition key (normalized)',
    `user_id`      VARCHAR(36)           COMMENT 'Nullable: specific user authorized for this workflow',
    `group_id`     BIGINT                COMMENT 'Nullable: specific group authorized for this workflow',
    `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    -- Unique constraints prevent duplicate assignments for both user and group paths
    UNIQUE KEY `uk_wf_auth_user`  (`workflow_key`, `user_id`),
    UNIQUE KEY `uk_wf_auth_group` (`workflow_key`, `group_id`),
    INDEX `idx_wfa_workflow` (`workflow_key`),
    INDEX `idx_wfa_user`     (`user_id`),
    INDEX `idx_wfa_group`    (`group_id`),
    CONSTRAINT `fk_wfa_group` FOREIGN KEY (`group_id`) REFERENCES `app_groups` (`id`) ON DELETE CASCADE,
    -- Business rule: exactly one of user_id / group_id must be set
    CONSTRAINT `chk_wfa_subject` CHECK (
        (`user_id` IS NOT NULL AND `group_id` IS NULL) OR
        (`user_id` IS NULL AND `group_id` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 4: task_routing_rules
-- Defines HOW a specific task step inside a workflow is routed.
-- routing_type drives the assignment logic in the Task Interceptor.
CREATE TABLE IF NOT EXISTS `task_routing_rules` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `workflow_key`    VARCHAR(100) NOT NULL COMMENT 'Camunda process definition key',
    `task_id`         VARCHAR(100) NOT NULL COMMENT 'BPMN UserTask element ID',
    `routing_type`    ENUM(
                        'STAR',         -- Assign to the workflow initiator (starterUserId variable)
                        'DYNAMIC_USER', -- Round-robin / lowest workload across authorized users
                        'SELECT_USER',  -- Assign to a specific, statically-selected user
                        'SELECT_GROUP'  -- Assign to a group as candidateGroup (group claim model)
                      ) NOT NULL DEFAULT 'STAR',
    `target_user_id`  VARCHAR(36)  NULL COMMENT 'Required when routing_type = SELECT_USER',
    `target_group_id` BIGINT       NULL COMMENT 'Required when routing_type = SELECT_GROUP',
    `created_at`      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_routing` (`workflow_key`, `task_id`),
    INDEX `idx_trr_lookup` (`workflow_key`, `task_id`),
    INDEX `idx_trr_group`  (`target_group_id`),
    CONSTRAINT `fk_trr_group` FOREIGN KEY (`target_group_id`) REFERENCES `app_groups` (`id`) ON DELETE SET NULL,
    -- Business rule: SELECT_USER needs target_user_id; SELECT_GROUP needs target_group_id
    CONSTRAINT `chk_trr_select_user`  CHECK (`routing_type` != 'SELECT_USER'  OR `target_user_id`  IS NOT NULL),
    CONSTRAINT `chk_trr_select_group` CHECK (`routing_type` != 'SELECT_GROUP' OR `target_group_id` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- Verification queries (run these to confirm schema is correct)
-- ============================================================
-- SHOW TABLES LIKE '%group%';
-- SHOW TABLES LIKE '%workflow%';
-- SHOW TABLES LIKE '%task_routing%';
-- DESCRIBE workflow_authorizations;
-- DESCRIBE task_routing_rules;
