-- Create database for manual user testing
CREATE DATABASE camunda_manual;

-- Create user with restricted privileges for manual user testing
CREATE USER 'camunda_user'@'%' IDENTIFIED BY 'Camunda_Pass123!';

-- Grant specific privileges for Camunda operations on the camunda_manual database
-- Table and schema operations
GRANT CREATE, DROP, ALTER ON camunda_manual.* TO 'camunda_user'@'%';

-- Index and foreign key operations
GRANT INDEX, REFERENCES ON camunda_manual.* TO 'camunda_user'@'%';

-- CRUD operations
GRANT SELECT, INSERT, UPDATE, DELETE ON camunda_manual.* TO 'camunda_user'@'%';

-- View and routine operations
GRANT CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE ON camunda_manual.* TO 'camunda_user'@'%';

-- Additional operations for Camunda
GRANT CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE, TRIGGER, DROP ON camunda_manual.* TO 'camunda_user'@'%';

-- Flush privileges to ensure they take effect
FLUSH PRIVILEGES;
