-- Grant the restricted `camunda` matrix user permission to create and fully
-- manage the per-physical-tenant databases provisioned at test runtime by
-- CamundaMultiDBExtension / PhysicalTenantSchemaProvisioner for
-- @MultiDbPhysicalTenants tests.
--
-- The MySQL/MariaDB images only grant the MYSQL_USER privileges on the single
-- MYSQL_DATABASE (`camunda.*`), so it cannot CREATE DATABASE for the per-PT
-- namespaces. This is a throwaway test database, so a broad grant is acceptable.
-- Runs from /docker-entrypoint-initdb.d after the image has created the user.
GRANT ALL PRIVILEGES ON *.* TO 'camunda'@'%';
FLUSH PRIVILEGES;

