#!/usr/bin/env bash
# Shared settings for every demo script. Sourced, not executed.

set -euo pipefail

# Camunda 8 Run REST base address.
BASE_URL="${BASE_URL:-http://localhost:8080}"

DEMO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELS_DIR="$DEMO_ROOT/models"

# The file-based secret store's directory. Must match camunda.secrets.stores.file.default.path,
# which demo-config.yaml reads from this same variable. Git-ignored, so no secret file is ever
# committed.
SECRET_DEMO_DIR="${SECRET_DEMO_DIR:-$DEMO_ROOT/secrets}"

# Admin user created by demo-config.yaml. Holds SECRET:READ and SECRET:REVEAL on "*" through the
# default admin role, so it needs no explicit grant.
ADMIN_USER="${ADMIN_USER:-demo}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-demo}"

# Non-admin users created during the permission scenes. Same password for all of them to keep the
# demo readable; these accounts exist only on a local machine.
DEMO_PASSWORD="${DEMO_PASSWORD:-password123}"

# POSTs a JSON body as the given user and pretty-prints the response.
# Usage: api_post <user> <password> <path> <json-body>
api_post() {
  local user="$1" password="$2" path="$3" body="${4:-}"
  local args=(-sS -u "$user:$password" -H 'Content-Type: application/json' -X POST "$BASE_URL$path")
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  curl "${args[@]}" | jq .
}

# Same, but prints the HTTP status line alongside the body. Used where the status is the point
# (a rejected deployment, an unauthenticated call).
# Usage: api_post_verbose <user> <password> <path> <json-body>
api_post_verbose() {
  local user="$1" password="$2" path="$3" body="${4:-}"
  local args=(-sS -u "$user:$password" -w '\nHTTP %{http_code}\n'
    -H 'Content-Type: application/json' -X POST "$BASE_URL$path")
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  curl "${args[@]}"
}
