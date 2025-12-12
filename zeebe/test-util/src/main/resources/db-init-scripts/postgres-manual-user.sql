-- Create user with restricted privileges for manual user testing
CREATE USER camunda_user WITH PASSWORD 'Camunda_Pass123!';

-- Create database for manual user testing
CREATE DATABASE camunda_manual OWNER camunda_user;

-- Grant connection privilege on the camunda_manual database
GRANT CONNECT ON DATABASE camunda_manual TO camunda_user;

-- Connect to the camunda_manual database to grant schema privileges
\c camunda_manual

-- Grant usage and creation on public schema
GRANT USAGE ON SCHEMA public TO camunda_user;
GRANT CREATE ON SCHEMA public TO camunda_user;

-- Grant specific privileges for tables, views, and sequences
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public TO camunda_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO camunda_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO camunda_user;

-- Grant privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON TABLES TO camunda_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO camunda_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO camunda_user;
