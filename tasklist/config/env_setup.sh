#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#

#!/bin/bash
WITH_IDENTITY=$1
WITH_OAUTH=$2
WITH_MULTI_TENANCY=$3

IDENTITY_CONFIG=/tmp/.env.identity.tmp
IDENTITY_OAUTH_CONFIG=/tmp/.env.identity-oauth.tmp
DATABASE_CONFIG=/tmp/.env.database.${DATABASE}.tmp
MULTI_TENANCY_CONFIG=/tmp/.env.multi-tenancy.tmp
MAVEN_CONTEXT=/tmp/.env.maven-context.tmp

## Dump configuration to temporary file to source during Maven execution
{
  grep -v '^#' "$DATABASE_CONFIG"
  [ "$WITH_IDENTITY" = "true" ] && grep -v '^#' "$IDENTITY_CONFIG" || true
  [ "$WITH_OAUTH" = "true" ] && grep -v '^#' "$IDENTITY_OAUTH_CONFIG" || true
  [ "$WITH_MULTI_TENANCY" = "true" ] && grep -v '^#' "$MULTI_TENANCY_CONFIG" || true
} > $MAVEN_CONTEXT

# Source the MAVEN_CONTEXT file
set -o allexport
source $MAVEN_CONTEXT
set +o allexport

# Resolve placeholders
eval DATABASE_URL=\$EXT_${DATABASE_CAPS}_URL
eval CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=\$EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS
eval CAMUNDA_TASKLIST_${DATABASE_CAPS}_URL=\${EXT_${DATABASE_CAPS}_URL}
eval CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=\${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS}

# Replace Tasklist URLs for Zeebe & ES/OS with Docker external URLs
./config/write_or_replace_var.sh "CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS" "$CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS" $MAVEN_CONTEXT
./config/write_or_replace_var.sh "CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS" "$CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS" $MAVEN_CONTEXT
./config/write_or_replace_var.sh "CAMUNDA_DATABASE_URL" "$DATABASE_URL" $MAVEN_CONTEXT

# Replace Zeebe database config with Docker external URLs for running Zeebe in single app
./config/write_or_replace_var.sh "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL" "$DATABASE_URL" $MAVEN_CONTEXT

# Cleanup temporary files
for file in $IDENTITY_CONFIG $IDENTITY_OAUTH_CONFIG $DATABASE_CONFIG $MULTI_TENANCY_CONFIG; do
  if [ -e "$file" ]; then
    rm -rf "$file"
  fi
done
