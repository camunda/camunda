#!/usr/bin/env bash
# Cross-tenant API smoke for the Physical-Tenant Security PoC.
#
# Both supported PT API URL schemes (spec D7) are exercised:
#   * Webapp-aligned: /physical-tenant/<id>/v2/whoami   — inside the per-tenant
#     session cookie's Path scope (cookie auth or bearer auth both work).
#   * Direct API client: /v2/physical-tenants/<id>/whoami — outside the cookie's
#     Path scope (bearer-only in practice).
#
# For each scheme we run the same 5-cell matrix:
#   tenant-A token → /<scheme>/tenanta/whoami → 200
#   tenant-A token → /<scheme>/default/whoami → 403
#   default token  → /<scheme>/default/whoami → 200
#   default token  → /<scheme>/tenanta/whoami → 403
#   no token       → /<scheme>/tenanta/whoami → 401
#
# Requires: ./pt-poc-idp.sh and ./pt-poc-oc.sh running.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"
KC_DEFAULT="http://localhost:8081/realms/default/protocol/openid-connect/token"

# URL templates — %s gets substituted with the target tenant id.
WEBAPP_URL_TEMPLATE="/physical-tenant/%s/v2/whoami"
APICLIENT_URL_TEMPLATE="/v2/physical-tenants/%s/whoami"

token() {
  local url="$1" client_id="$2" client_secret="$3" user="$4" pass="$5"
  curl -fsS -X POST "$url" \
    -d "grant_type=password" \
    -d "client_id=$client_id" \
    -d "client_secret=$client_secret" \
    -d "username=$user" \
    -d "password=$pass" \
    | jq -r .access_token
}

call() {
  local label="$1" token="$2" path="$3" expected="$4"
  local status
  if [[ -n "$token" ]]; then
    status=$(curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" -H "Authorization: Bearer $token" "$OC$path")
  else
    status=$(curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" "$OC$path")
  fi
  printf "%-22s %s %s  " "$label" "$status" "$path"
  if [[ "$status" == "$expected" ]]; then
    echo "OK"
  else
    echo "FAIL (expected $expected)"
    cat /tmp/pt-poc-api-body
    echo
  fi
}

matrix() {
  local label="$1" template="$2"
  echo "=== Cross-tenant matrix — $label ==="
  call "tenanta -> tenanta" "$TA"  "$(printf "$template" tenanta)" 200
  call "tenanta -> default" "$TA"  "$(printf "$template" default)" 403
  call "default -> default" "$DEF" "$(printf "$template" default)" 200
  call "default -> tenanta" "$DEF" "$(printf "$template" tenanta)" 403
  call "no token -> tenanta" ""    "$(printf "$template" tenanta)" 401
  echo
}

echo "=== Acquiring tokens ==="
TA=$(token "$KC_TENANTA" camunda-pt-tenanta-client tenanta-secret bob bob)
DEF=$(token "$KC_DEFAULT" camunda-pt-default-client default-secret alice alice)
echo "tenanta token: ${TA:0:40}..."
echo "default token: ${DEF:0:40}..."
echo

matrix "Webapp-relative API Path" "$WEBAPP_URL_TEMPLATE"
matrix "API Path"    "$APICLIENT_URL_TEMPLATE"

# Default tenant on the unprefixed access path (Task 12) — /v2/whoami is served by the
# unprefixed default API chain (cookie scoped at Path=/, name camunda-session-default-root).
# Single tenant target (default), so this is a 3-cell block rather than a full cross-tenant
# matrix.
echo "=== Default unprefixed URL (cookie scoped at Path=/) ==="
call "default -> default" "$DEF" "/v2/whoami" 200
call "tenanta -> default" "$TA"  "/v2/whoami" 403
call "no token -> default" ""    "/v2/whoami" 401
echo

rm -f /tmp/pt-poc-api-body
