#!/usr/bin/env bash
#
# Copyright Camunda Services GmbH ...
#
set -euo pipefail

# Usage: ./createCredsLoadTest.sh [namespace]
NS="${1:-__NAMESPACE__}"

# Check if the secret already exists; if so, retrieve the existing orchestration secret
# for the sed replacement and skip creation.
if kubectl -n "$NS" get secret camunda-credentials &>/dev/null; then
  echo "Secret 'camunda-credentials' already exists in namespace '$NS'. Skipping creation."
  ORCHESTRATION_SECRET=$(kubectl -n "$NS" get secret camunda-credentials -o jsonpath='{.data.orchestration-security-authentication-oidc-secret}' | base64 -d)
  echo "Replacing __SECRET__ in load-test-values.yaml with existing orchestration secret."
  sed_inplace "s/__SECRET__/${ORCHESTRATION_SECRET}/g" load-test-values.yaml
  exit 0
fi

# Helper: generate a random 20-char alphanumeric password (no special characters)
gen_password() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 20; }

# Helper: generate a 32-char hex token for OIDC client secrets
gen_token() { openssl rand -hex 16; }

# You can optionally export any of these beforehand to control their values.
: "${IDENTITY_FIRSTUSER_PASSWORD:=$(gen_password)}"
: "${IDENTITY_KEYCLOAK_ADMIN_PASSWORD:=$(gen_password)}"
: "${IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD:=$(gen_password)}"
: "${IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD:=$(gen_password)}"
: "${IDENTITY_POSTGRESQL_ADMIN_PASSWORD:=$(gen_password)}"
: "${IDENTITY_POSTGRESQL_USER_PASSWORD:=$(gen_password)}"

# OIDC client secrets (32-char hex strings)
: "${IDENTITY_ADMIN_CLIENT_TOKEN:=$(gen_token)}"
: "${IDENTITY_OPTIMIZE_CLIENT_TOKEN:=$(gen_token)}"

echo "Creating camunda-credentials in namespace '$NS'..."

kubectl -n "$NS" apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: camunda-credentials
type: Opaque
stringData:
  identity-firstuser-password: "${IDENTITY_FIRSTUSER_PASSWORD}"
  identity-keycloak-admin-password: "${IDENTITY_KEYCLOAK_ADMIN_PASSWORD}"
  identity-keycloak-postgresql-admin-password: "${IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD}"
  identity-keycloak-postgresql-user-password: "${IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD}"
  identity-postgresql-admin-password: "${IDENTITY_POSTGRESQL_ADMIN_PASSWORD}"
  identity-postgresql-user-password: "${IDENTITY_POSTGRESQL_USER_PASSWORD}"
  identity-admin-client-token: "${IDENTITY_ADMIN_CLIENT_TOKEN}"
  identity-optimize-client-token: "${IDENTITY_OPTIMIZE_CLIENT_TOKEN}"
EOF

echo "Done. Secret 'camunda-credentials' created in namespace '$NS'."
