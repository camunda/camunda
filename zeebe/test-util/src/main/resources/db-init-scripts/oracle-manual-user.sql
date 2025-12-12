-- Connect to pluggable database
ALTER SESSION SET CONTAINER = FREEPDB1;

-- Create user with restricted privileges for manual user testing
-- In Oracle, user acts as schema, so this creates both user and schema
CREATE USER camunda_user IDENTIFIED BY "Camunda_Pass123!"
  DEFAULT TABLESPACE USERS
  TEMPORARY TABLESPACE TEMP
  QUOTA UNLIMITED ON USERS;

-- Grant necessary system privileges for Camunda operations
-- Session access
GRANT CREATE SESSION TO camunda_user;

-- Table and schema operations
GRANT CREATE TABLE TO camunda_user;
GRANT ALTER ANY TABLE TO camunda_user;
GRANT DROP ANY TABLE TO camunda_user;

-- View operations
GRANT CREATE VIEW TO camunda_user;

-- Sequence operations
GRANT CREATE SEQUENCE TO camunda_user;

-- Procedure and trigger operations
GRANT CREATE PROCEDURE TO camunda_user;
GRANT CREATE TRIGGER TO camunda_user;

-- Type and synonym operations
GRANT CREATE TYPE TO camunda_user;
GRANT CREATE SYNONYM TO camunda_user;

-- CRUD operations on own schema
GRANT SELECT ANY TABLE TO camunda_user;
GRANT INSERT ANY TABLE TO camunda_user;
GRANT UPDATE ANY TABLE TO camunda_user;
GRANT DELETE ANY TABLE TO camunda_user;
