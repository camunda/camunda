#!/usr/bin/env bash
# INTERIM — drop before review. Scenario D of the PT API smoke harness.
#
# Verifies PT-to-PT selection among multiple NAMED cluster providers (#54730).
#
# Boot with the scenario-D overlay first:  ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-two-tenants
# Both `tenanta` and `tenantb` are cluster providers (distinct Keycloak realms on :8082 and :8083),
# and each tenant is assigned only its own: tenanta=[tenanta] (base), tenantb=[tenantb] (overlay).
# So each tenant's chain narrows out the other's provider — a tenantb token (a valid cluster
# provider) is rejected on /pt/tenanta and vice versa. This is the named-provider selection /
# PT-to-PT isolation the base harness cannot show (it only has the default slot + tenanta).
#
# Requires: ./pt-smoke-test-idp.sh and ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-two-tenants running.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"
KC_TENANTB="http://localhost:8083/realms/tenantb/protocol/openid-connect/token"

PROBE_PATH_TEMPLATE="/physical-tenants/%s/v2/authentication/me"

PASS=0
FAIL=0

token() {
  local url="$1" client_id="$2" client_secret="$3" user="$4" pass="$5"
  curl -fsS -X POST "$url" \
    -d "grant_type=password" -d "client_id=$client_id" -d "client_secret=$client_secret" \
    -d "username=$user" -d "password=$pass" | jq -r .access_token
}

_status() {
  local tok="$1" path="$2"
  if [[ -n "$tok" ]]; then
    curl -sS -o /tmp/pt-smoke-test-api-body -w "%{http_code}" -H "Authorization: Bearer $tok" "$OC$path"
  else
    curl -sS -o /tmp/pt-smoke-test-api-body -w "%{http_code}" "$OC$path"
  fi
}

check_not_401() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-74s %s  " "$label" "$status"
  if [[ "$status" != "401" ]]; then echo "PASS (not 401)"; PASS=$((PASS + 1))
  else echo "FAIL (got 401)"; cat /tmp/pt-smoke-test-api-body 2>/dev/null && echo; FAIL=$((FAIL + 1)); fi
}

check_401() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-74s %s  " "$label" "$status"
  if [[ "$status" == "401" ]]; then echo "PASS (401 as expected)"; PASS=$((PASS + 1))
  else echo "FAIL (expected 401, got $status)"; cat /tmp/pt-smoke-test-api-body 2>/dev/null && echo; FAIL=$((FAIL + 1)); fi
}

echo "=== Acquiring tokens ==="
TA=$(token "$KC_TENANTA" camunda-pt-tenanta-client tenanta-secret bob bob)
TB=$(token "$KC_TENANTB" camunda-pt-tenantb-client tenantb-secret carol carol)
echo "  tenanta token (bob,   :8082, aud=pt-tenanta-aud)"
echo "  tenantb token (carol, :8083, aud=pt-tenantb-aud)"
echo

echo "=== Each tenant accepts its own provider ==="
check_not_401 "tenanta token -> /pt/tenanta  (own provider)" "$TA" "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
check_not_401 "tenantb token -> /pt/tenantb  (own provider)" "$TB" "$(printf "$PROBE_PATH_TEMPLATE" tenantb)"
echo

echo "=== PT-to-PT selection: each rejects the OTHER's provider (a valid cluster provider) ==="
check_401 "tenantb token -> /pt/tenanta  (tenantb is a cluster provider, not assigned to tenanta)" "$TB" "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
check_401 "tenanta token -> /pt/tenantb  (tenanta is a cluster provider, not assigned to tenantb)" "$TA" "$(printf "$PROBE_PATH_TEMPLATE" tenantb)"
check_401 "no token      -> /pt/tenantb  (unauthenticated rejected)"                                ""   "$(printf "$PROBE_PATH_TEMPLATE" tenantb)"
echo

rm -f /tmp/pt-smoke-test-api-body
echo "=== Results: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -gt 0 ]] && exit 1 || exit 0
