-- Create database for manual user testing
CREATE DATABASE camunda_manual;
GO

-- Switch to the camunda_manual database
USE camunda_manual;
GO

-- Create login and user with restricted privileges for manual user testing
-- Note: Password is hardcoded for testing purposes only
CREATE LOGIN camunda_user WITH PASSWORD = 'Camunda_Pass123!';
GO

CREATE USER camunda_user FOR LOGIN camunda_user;
GO

-- Grant specific privileges for Camunda operations directly to the user
-- Schema and table operations
GRANT CREATE TABLE TO camunda_user;
GRANT ALTER TO camunda_user;
GRANT DROP TO camunda_user;

-- View operations
GRANT CREATE VIEW TO camunda_user;

-- Procedure and function operations
GRANT CREATE PROCEDURE TO camunda_user;
GRANT CREATE FUNCTION TO camunda_user;

-- CRUD operations
GRANT SELECT TO camunda_user;
GRANT INSERT TO camunda_user;
GRANT UPDATE TO camunda_user;
GRANT DELETE TO camunda_user;

-- Additional operations
GRANT EXECUTE TO camunda_user;
GRANT REFERENCES TO camunda_user;
GRANT CONTROL TO camunda_user;
GO
