#!/usr/bin/env bash
# Creates a one-shot Kubernetes Job that sets up Camunda authorizations for the
# orchestration client using the admin client credentials (client_credentials grant).
# The admin client is registered in Keycloak via global.identity.auth.admin in the
# Camunda Platform Helm chart values.
#
# Usage: ./setup-authorizations.sh [namespace]
set -euo pipefail

NS="${1:-__NAMESPACE__}"

echo "Setting up authorizations Job in namespace '$NS'..."

# Delete previous run if it still exists (e.g. within ttlSecondsAfterFinished window)
kubectl -n "$NS" delete job setup-authorizations --ignore-not-found

kubectl -n "$NS" apply -f - <<'EOF'
apiVersion: batch/v1
kind: Job
metadata:
  name: setup-authorizations
  labels:
    app: setup-authorizations
spec:
  ttlSecondsAfterFinished: 300
  backoffLimit: 10
  template:
    spec:
      restartPolicy: OnFailure
      containers:
        - name: setup-authorizations
          image: docker.io/alpine:3.20
          command: ["/bin/sh", "-c"]
          args:
            - |
              apk add --no-cache curl jq >/dev/null 2>&1

              echo "Waiting for Keycloak..."
              until curl -s --connect-timeout 5 -o /dev/null \
                "http://keycloak/auth/realms/camunda-platform/.well-known/openid-configuration"; do
                echo "  not ready, retrying in 10s..."
                sleep 10
              done
              echo "Keycloak ready"

              echo "Waiting for Camunda gateway..."
              until curl -s --connect-timeout 5 -o /dev/null \
                "http://camunda-gateway:8080"; do
                echo "  not ready, retrying in 10s..."
                sleep 10
              done
              echo "Camunda gateway ready"

              echo "Fetching admin token..."
              ADMIN_TOKEN=$(curl -sf -X POST \
                "http://keycloak/auth/realms/camunda-platform/protocol/openid-connect/token" \
                -H "Content-Type: application/x-www-form-urlencoded" \
                -d "grant_type=client_credentials" \
                -d "client_id=admin" \
                -d "client_secret=${ADMIN_CLIENT_SECRET}" | jq -r '.access_token')

              if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
                echo "Failed to get admin token — check that global.identity.auth.admin is enabled"
                exit 1
              fi
              echo "Got admin token"

              create_authorization() {
                local resource_type=$1
                local permissions=$2
                HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
                  -X POST "http://camunda-gateway:8080/v2/authorizations" \
                  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
                  -H "Content-Type: application/json" \
                  -d "{\"ownerId\":\"${OWNER_ID}\",\"ownerType\":\"CLIENT\",\"resourceId\":\"*\",\"resourceType\":\"${resource_type}\",\"permissionTypes\":${permissions}}")
                echo "  ${resource_type}: HTTP ${HTTP_CODE}"
                # 201 = created, 409 = already exists — both are acceptable
                [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "409" ] || {
                  echo "Unexpected HTTP ${HTTP_CODE} for ${resource_type}"
                  exit 1
                }
              }

              echo "Creating authorizations for client '${OWNER_ID}'..."
              create_authorization "RESOURCE" '["CREATE"]'
              create_authorization "PROCESS_DEFINITION" '["CREATE_PROCESS_INSTANCE","UPDATE_PROCESS_INSTANCE","READ_PROCESS_INSTANCE","READ_PROCESS_DEFINITION"]'

              echo "Done. Authorizations set up successfully."
          env:
            - name: OWNER_ID
              value: "orchestration"
            - name: ADMIN_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: camunda-credentials
                  key: identity-admin-client-token
EOF

echo "Waiting for setup-authorizations Job to complete (timeout 10m)..."
kubectl -n "$NS" wait --for=condition=complete job/setup-authorizations --timeout=600s
echo "Authorization setup complete."
