#!/usr/bin/env bash
# Cross-tenant API smoke for the Physical-Tenant Security PoC.
#
# Acquires an access token from one tenant's Keycloak via the password grant
# and exercises the four cells of the cross-tenant matrix:
#   tenant-A token → /v2/physical-tenants/tenanta/whoami   → 200
#   tenant-A token → /v2/physical-tenants/default/whoami   → 403
#   default token  → /v2/physical-tenants/default/whoami   → 200
#   default token  → /v2/physical-tenants/tenanta/whoami   → 403
#
# Requires: ./pt-poc-idp.sh and ./pt-poc-oc.sh running.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"
KC_DEFAULT="http://localhost:8081/realms/default/protocol/openid-connect/token"

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
  local body status
  body=$(curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" -H "Authorization: Bearer $token" "$OC$path")
  status="$body"
  printf "%-22s %s %s  " "$label" "$status" "$path"
  if [[ "$status" == "$expected" ]]; then
    echo "OK"
  else
    echo "FAIL (expected $expected)"
    cat /tmp/pt-poc-api-body
    echo
  fi
}

echo "=== Acquiring tokens ==="
TA=$(token "$KC_TENANTA" camunda-pt-tenanta-client tenanta-secret bob bob)
DEF=$(token "$KC_DEFAULT" camunda-pt-default-client default-secret alice alice)
echo "tenanta token: ${TA:0:40}..."
echo "default token: ${DEF:0:40}..."
echo

echo "=== Cross-tenant matrix ==="
call "tenanta -> tenanta"  "$TA"  "/v2/physical-tenants/tenanta/whoami"  200
call "tenanta -> default"  "$TA"  "/v2/physical-tenants/default/whoami"  403
call "default -> default"  "$DEF" "/v2/physical-tenants/default/whoami"  200
call "default -> tenanta"  "$DEF" "/v2/physical-tenants/tenanta/whoami"  403
echo

echo "=== Unauthenticated probe (should be 401) ==="
call "no token -> tenanta"  ""   "/v2/physical-tenants/tenanta/whoami"  401

rm -f /tmp/pt-poc-api-body
