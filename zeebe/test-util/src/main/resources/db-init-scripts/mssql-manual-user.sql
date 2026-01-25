-- Create login and user with restricted privileges for manual user testing
-- Note: Password is hardcoded for testing purposes only
CREATE LOGIN camunda WITH PASSWORD = 'Camunda_Pass123!';

CREATE USER camunda FOR LOGIN camunda;

-- CRUD operations
GRANT SELECT TO camunda;
GRANT INSERT TO camunda;
GRANT UPDATE TO camunda;
GRANT DELETE TO camunda;

-- Allow truncate table operations
GRANT ALTER TO camunda;

