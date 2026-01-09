-- ============================================================================
-- Database Migration: Create Teachers Table for Authentication
-- Version: 002
-- Description: Creates teachers table with BCrypt encrypted passwords,
--              sync fields, audit fields, and performance indexes
-- Author: EduPro-Teacher Team
-- Date: 2025-11-29
-- ============================================================================

-- Drop table if exists (for development/testing)
DROP TABLE IF EXISTS teachers CASCADE;

-- Create teachers table
CREATE TABLE teachers (
    -- Primary Key
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Authentication Fields
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,  -- BCrypt encrypted (60 chars typical)

    -- Profile Information
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    department VARCHAR(100),

    -- Status
    active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Sync Fields (for PostgreSQL synchronization)
    sync_status VARCHAR(20) DEFAULT 'pending',
    last_sync_time TIMESTAMP,

    -- Audit Fields
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Performance Indexes
-- ============================================================================

-- Index for login lookups (most critical)
CREATE INDEX idx_teacher_employee_id ON teachers(employee_id);

-- Index for email lookups
CREATE INDEX idx_teacher_email ON teachers(email);

-- Index for active status filtering
CREATE INDEX idx_teacher_active ON teachers(active);

-- Index for sync operations
CREATE INDEX idx_teacher_sync_status ON teachers(sync_status);

-- Composite index for active teachers needing sync
CREATE INDEX idx_teacher_active_sync ON teachers(active, sync_status);

-- ============================================================================
-- Comments
-- ============================================================================

COMMENT ON TABLE teachers IS 'Teacher authentication and profile information';
COMMENT ON COLUMN teachers.id IS 'Primary key - auto-incremented';
COMMENT ON COLUMN teachers.employee_id IS 'Unique employee ID used for login';
COMMENT ON COLUMN teachers.password IS 'BCrypt encrypted password (60 chars)';
COMMENT ON COLUMN teachers.first_name IS 'Teacher first name';
COMMENT ON COLUMN teachers.last_name IS 'Teacher last name';
COMMENT ON COLUMN teachers.email IS 'Teacher email address';
COMMENT ON COLUMN teachers.department IS 'Department (e.g., Mathematics, English)';
COMMENT ON COLUMN teachers.active IS 'Account active status (true = can login)';
COMMENT ON COLUMN teachers.sync_status IS 'Sync status: pending, synced, conflict';
COMMENT ON COLUMN teachers.last_sync_time IS 'Last successful sync timestamp';
COMMENT ON COLUMN teachers.created_date IS 'Account creation timestamp';
COMMENT ON COLUMN teachers.modified_date IS 'Last modification timestamp';

-- ============================================================================
-- Validation
-- ============================================================================

-- Ensure employee_id is not empty
ALTER TABLE teachers ADD CONSTRAINT chk_employee_id_not_empty
    CHECK (LENGTH(TRIM(employee_id)) > 0);

-- Ensure password is not empty (BCrypt hashes are 60 chars)
ALTER TABLE teachers ADD CONSTRAINT chk_password_not_empty
    CHECK (LENGTH(password) >= 10);

-- Ensure first_name is not empty
ALTER TABLE teachers ADD CONSTRAINT chk_first_name_not_empty
    CHECK (LENGTH(TRIM(first_name)) > 0);

-- Ensure last_name is not empty
ALTER TABLE teachers ADD CONSTRAINT chk_last_name_not_empty
    CHECK (LENGTH(TRIM(last_name)) > 0);

-- Ensure email format is valid (basic check)
ALTER TABLE teachers ADD CONSTRAINT chk_email_format
    CHECK (email LIKE '%@%' AND email LIKE '%.%');

-- Ensure sync_status has valid values
ALTER TABLE teachers ADD CONSTRAINT chk_sync_status_valid
    CHECK (sync_status IN ('pending', 'synced', 'conflict', 'error'));

-- ============================================================================
-- Migration Complete
-- ============================================================================

-- Log migration completion
SELECT 'V002__create_teachers_table.sql migration completed successfully' AS migration_status;
