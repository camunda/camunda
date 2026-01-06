-- Create user with restricted privileges for manual user testing
CREATE USER 'camunda'@'%' IDENTIFIED BY 'Camunda_Pass123!';

-- CRUD operations
GRANT SELECT, INSERT, UPDATE, DELETE ON camunda.* TO 'camunda'@'%';

-- need the DROP privilege to TRUNCATE tabl
GRANT DROP ON camunda.* TO 'camunda'@'%';

-- Flush privileges to ensure they take effect
FLUSH PRIVILEGES;
