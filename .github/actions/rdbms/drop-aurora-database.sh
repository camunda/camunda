#!/bin/bash

# Terminate all connections to the database
psql postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${PGDATABASE}' AND pid <> pg_backend_pid();"

# Drop the database
psql postgres -c "DROP DATABASE IF EXISTS \"${PGDATABASE}\";"
