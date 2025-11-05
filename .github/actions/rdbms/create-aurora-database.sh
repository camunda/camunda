#!/bin/bash

psql postgres -c "DROP DATABASE IF EXISTS \"${PGDATABASE}\";" \
  -c "CREATE DATABASE \"${PGDATABASE}\";"

# see https://github.com/camunda/infra-core/tree/opensearch-cluster/camunda-opensearch#user-setup
psql postgres -c "CREATE USER \"${PGUSER_IRSA}\";" \
  -c "GRANT rds_iam TO \"${PGUSER_IRSA}\";" \
  -c "GRANT rds_superuser TO \"${PGUSER_IRSA}\";" \
  -c "GRANT ALL PRIVILEGES ON DATABASE \"${PGDATABASE}\" TO \"${PGUSER_IRSA}\";"

psql postgres -c "SELECT aurora_version();" \
  -c "SELECT version();"
