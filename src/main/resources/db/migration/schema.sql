-- ============================================================================
-- CAMUNDA DECOUPLING ARCHITECTURE - DATABASE MIGRATION SCRIPT (MySQL)
-- Target Database: camunda_custom_db
-- Description: Migrates identity, authentication, RBAC, and assignment routing
-- away from the Camunda Engine into custom MySQL tables.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS camunda_custom_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE camunda_custom_db;

-- ----------------------------------------------------------------------------
-- 1. APP_ROLES: Role-Based Access Control (RBAC) Definitions
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 2. APP_USERS: Custom User Identity & Credentials
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_users (
    id VARCHAR(36) NOT NULL PRIMARY KEY, -- Standard UUID representation
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_app_users_username (username),
    INDEX idx_app_users_email (email),
    INDEX idx_app_users_status (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 3. USER_ROLES: RBAC Mapping Table (Many-to-Many)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES app_roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 4. USER_WORKFLOW_ASSIGNMENTS: Maps users to workflows for DYNAMIC routing
-- References app_users.id (UUID)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_workflow_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    workflow_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_workflow (user_id, workflow_id),
    INDEX idx_user_wf_workflow (workflow_id),
    CONSTRAINT fk_user_wf_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 5. TASK_ASSIGNMENT_RULES: Custom routing rules (STAR, DYNAMIC, SELECT)
-- References app_users.id (UUID) for SELECT assignee
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_assignment_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(100) NOT NULL,
    task_id VARCHAR(100) NOT NULL,
    assignment_type VARCHAR(20) NOT NULL, -- 'STAR', 'DYNAMIC', 'SELECT'
    assignee_user_id VARCHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_task_rule (workflow_id, task_id),
    INDEX idx_task_rule_lookup (workflow_id, task_id),
    CONSTRAINT fk_task_rule_assignee FOREIGN KEY (assignee_user_id) REFERENCES app_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- SEED INITIAL DATA
-- ============================================================================

-- Roles: ROLE_ADMIN and ROLE_WORKER
INSERT INTO app_roles (id, role_name, description) VALUES
(1, 'ROLE_ADMIN', 'Global System Administrator with full access'),
(2, 'ROLE_WORKER', 'Standard workflow task worker')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- Default Admin User:
-- Username: admin
-- Password: Admin@1234
-- Email: admin@camunda-app.local
INSERT INTO app_users (id, username, password_hash, email, full_name, is_active, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'admin', '$2a$12$KRMAcQmMTXvDPnEY2gRope3HbqvcawFGpVxR9TBf3s95jwy/xoT4m', 'admin@camunda-app.local', 'System Administrator', b'1', NOW(), NOW())
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_active = VALUES(is_active);

-- Default Worker User:
-- Username: worker
-- Password: Worker@1234
-- Email: worker@camunda-app.local
INSERT INTO app_users (id, username, password_hash, email, full_name, is_active, created_at, updated_at) VALUES
('22222222-2222-2222-2222-222222222222', 'worker', '$2a$12$bE3laVWzg.CP6kdXaVGVF.eoMcq16Vifpb9U1NBy2T/rZV/GJkH8q', 'worker@camunda-app.local', 'Workflow Worker', b'1', NOW(), NOW())
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_active = VALUES(is_active);

-- Assign ROLE_ADMIN to Admin User
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
('11111111-1111-1111-1111-111111111111', 1);

-- Assign ROLE_WORKER to Worker User
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
('22222222-2222-2222-2222-222222222222', 2);

