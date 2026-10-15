#!/usr/bin/env bash
# INTERIM — drop before review. Scenario C of the PT API smoke harness.
#
# Verifies the reserved-`oidc` KEEP path (#54730) — the inverse of the base harness's [#54730] cell.
#
# Boot with the scenario-C overlay first:  ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-oidc-keep
# That overlay sets `camunda.physical-tenants.tenanta...providers.assigned: [oidc, tenanta]`, so
# tenanta KEEPS the inherited root default slot `oidc` alongside its own `tenanta` provider.
#
# Effect: a default-realm token (issuer :8081, the `oidc` slot) is ACCEPTED on /pt/tenanta — whereas
# the base harness (tenanta assigned [tenanta]) rejects it cross-issuer (./pt-smoke-test-api.sh).
#
# Requires: ./pt-smoke-test-idp.sh and ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-oidc-keep running.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_DEFAULT="http://localhost:8081/realms/default/protocol/openid-connect/token"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"

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

echo "=== Acquiring tokens ==="
DEF=$(token "$KC_DEFAULT" camunda-pt-default-client default-secret alice alice)
TA=$(token "$KC_TENANTA" camunda-pt-tenanta-client tenanta-secret bob bob)
echo "  default token (alice, :8081, aud=pt-default-aud)"
echo "  tenanta token (bob,   :8082, aud=pt-tenanta-aud)"
echo

echo "=== tenanta assigned [oidc, tenanta]: the default slot is KEPT on tenanta's chain ==="
check_not_401 "default token -> /pt/tenanta  (RESERVED-oidc KEEP: default realm accepted)" "$DEF" "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
check_not_401 "tenanta token -> /pt/tenanta  (own provider still accepted)"                 "$TA"  "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
echo "    ^ Inverse of the base [#54730] cell: there tenanta is [tenanta] only, so the default"
echo "      realm token is rejected (401) cross-issuer. Here 'oidc' is assigned, so it is accepted."
echo

rm -f /tmp/pt-smoke-test-api-body
echo "=== Results: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -gt 0 ]] && exit 1 || exit 0
