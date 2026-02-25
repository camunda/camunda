#!/usr/bin/env bash
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
set -euo pipefail

# Usage: ./createCredsLoadTest.sh [namespace]
NS="${1:-__NAMESPACE__}"

# Helper: generate a random 16-byte base64 secret
gen() { openssl rand -base64 16; }

# You can optionally export any of these beforehand to control their values.
: "${IDENTITY_FIRSTUSER_PASSWORD:=$(gen)}"
: "${IDENTITY_KEYCLOAK_ADMIN_PASSWORD:=$(gen)}"
: "${IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD:=$(gen)}"
: "${IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD:=$(gen)}"
: "${IDENTITY_POSTGRESQL_ADMIN_PASSWORD:=$(gen)}"
: "${IDENTITY_POSTGRESQL_USER_PASSWORD:=$(gen)}"

# OIDC client secrets
: "${IDENTITY_ADMIN_CLIENT_TOKEN:=$(gen)}"
: "${IDENTITY_OPTIMIZE_CLIENT_TOKEN:=$(gen)}"

echo "Creating/refreshing camunda-credentials in namespace '$NS'..."

kubectl -n "$NS" delete secret camunda-credentials --ignore-not-found

kubectl -n "$NS" create secret generic camunda-credentials \
  --from-literal=identity-firstuser-password="$IDENTITY_FIRSTUSER_PASSWORD" \
  --from-literal=identity-keycloak-admin-password="$IDENTITY_KEYCLOAK_ADMIN_PASSWORD" \
  --from-literal=identity-keycloak-postgresql-admin-password="$IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD" \
  --from-literal=identity-keycloak-postgresql-user-password="$IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD" \
  --from-literal=identity-postgresql-admin-password="$IDENTITY_POSTGRESQL_ADMIN_PASSWORD" \
  --from-literal=identity-postgresql-user-password="$IDENTITY_POSTGRESQL_USER_PASSWORD" \
  --from-literal=identity-admin-client-token="$IDENTITY_ADMIN_CLIENT_TOKEN" \
  --from-literal=identity-optimize-client-token="$IDENTITY_OPTIMIZE_CLIENT_TOKEN"

echo "Done. Secret 'camunda-credentials' created in namespace '$NS'."
