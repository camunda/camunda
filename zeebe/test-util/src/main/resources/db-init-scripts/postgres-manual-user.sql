-- Create user without privileges
CREATE USER camunda LOGIN PASSWORD 'Camunda_Pass123!' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;

-- Allow camunda to connect to the camunda database
REVOKE ALL ON DATABASE camunda FROM public;
GRANT CONNECT ON DATABASE camunda TO camunda;

-- Grant privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON TABLES TO camunda;
