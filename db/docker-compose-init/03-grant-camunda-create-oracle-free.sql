-- Grant the restricted `camunda` Oracle matrix user permission to create,
-- manage, and drop the per-physical-tenant users (== schemas) provisioned at
-- test runtime by CamundaMultiDBExtension / PhysicalTenantSchemaProvisioner for
-- @MultiDbPhysicalTenants tests. Oracle isolates physical tenants by
-- schema-per-user (a dedicated CREATE USER per tenant), see #56402.
--
-- The gvenzl images create APP_USER (`camunda`) with only CONNECT/RESOURCE on
-- its own schema, so it cannot CREATE USER / DROP USER, nor grant the
-- CONNECT/RESOURCE roles and UNLIMITED TABLESPACE the per-PT users need. This
-- is a throwaway test database, so a broad DBA grant is acceptable, mirroring
-- the MySQL/MariaDB grant script (02-grant-camunda-create-mysql.sql).
--
-- gvenzl runs *.sql init scripts as SYS connected to the CDB root, not the app
-- PDB where `camunda` is a local user, so switch into the PDB (FREEPDB1 for the
-- oracle-free image) before granting.
ALTER SESSION SET CONTAINER = FREEPDB1;
GRANT DBA TO CAMUNDA;
