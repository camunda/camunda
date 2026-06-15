#!/usr/bin/env bash
# INTERIM — drop before review. Scenario A of the PT API smoke harness.
#
# Verifies DEFAULT-tenant narrowing + the /v2 ≡ /physical-tenants/default unification (#54730).
#
# Boot with the scenario-A overlay first:  ./pt-poc-oc.sh pt-poc,pt-poc-default-narrowed
# That overlay sets `camunda.physical-tenants.default...providers.assigned: [tenanta]`, so the
# default tenant is narrowed to the named `tenanta` provider and the inherited root default slot
# `oidc` (the default realm on :8081) is dropped. Because the default tenant's resolved config also
# drives the unprefixed /v2 cluster chain, the limit applies to BOTH surfaces, identically.
#
# Contrast with the base harness (./pt-poc-api-smoke.sh), where the default tenant keeps the full
# set and a default-realm token is accepted on /v2 and /pt/default.
#
# Requires: ./pt-poc-idp.sh and ./pt-poc-oc.sh pt-poc,pt-poc-default-narrowed running.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_DEFAULT="http://localhost:8081/realms/default/protocol/openid-connect/token"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"

PROBE_PATH_TEMPLATE="/physical-tenants/%s/v2/authentication/me"
PROBE_CLUSTER="/v2/authentication/me"

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
    curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" -H "Authorization: Bearer $tok" "$OC$path"
  else
    curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" "$OC$path"
  fi
}

check_not_401() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-74s %s  " "$label" "$status"
  if [[ "$status" != "401" ]]; then echo "PASS (not 401)"; PASS=$((PASS + 1))
  else echo "FAIL (got 401)"; cat /tmp/pt-poc-api-body 2>/dev/null && echo; FAIL=$((FAIL + 1)); fi
}

check_401() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-74s %s  " "$label" "$status"
  if [[ "$status" == "401" ]]; then echo "PASS (401 as expected)"; PASS=$((PASS + 1))
  else echo "FAIL (expected 401, got $status)"; cat /tmp/pt-poc-api-body 2>/dev/null && echo; FAIL=$((FAIL + 1)); fi
}

# check_same: assert the same token yields the same pass/fail outcome on /v2 and /pt/default.
check_same() {
  local label="$1" tok="$2" s_cluster s_alias
  s_cluster=$(_status "$tok" "$PROBE_CLUSTER")
  s_alias=$(_status "$tok" "$(printf "$PROBE_PATH_TEMPLATE" default)")
  # Compare 401-vs-not-401 (authz 403s can vary), which is what isolation hinges on.
  local c_cls c_als
  [[ "$s_cluster" == "401" ]] && c_cls=401 || c_cls=ok
  [[ "$s_alias"   == "401" ]] && c_als=401 || c_als=ok
  printf "%-74s /v2=%s  /pt/default=%s  " "$label" "$s_cluster" "$s_alias"
  if [[ "$c_cls" == "$c_als" ]]; then echo "PASS (identical)"; PASS=$((PASS + 1))
  else echo "FAIL (surfaces diverge)"; FAIL=$((FAIL + 1)); fi
}

echo "=== Acquiring tokens ==="
DEF=$(token "$KC_DEFAULT" camunda-pt-default-client default-secret alice alice)
DVTA=$(token "$KC_TENANTA" camunda-pt-default-via-tenanta-client default-via-tenanta-secret bob bob)
echo "  default token       (alice, :8081, aud=pt-default-aud)"
echo "  default-via-tenanta (bob,   :8082, aud=pt-default-via-tenanta-aud)"
echo

echo "=== Default narrowed to [tenanta]: the root default slot 'oidc' is dropped cluster-wide ==="
check_401     "default token -> /v2          (default 'oidc' slot no longer in cluster providers)" "$DEF"  "$PROBE_CLUSTER"
check_401     "default token -> /pt/default  (same narrowed config as /v2)"                         "$DEF"  "$(printf "$PROBE_PATH_TEMPLATE" default)"
check_not_401 "dvta token    -> /v2          (assigned 'tenanta' provider still accepted)"          "$DVTA" "$PROBE_CLUSTER"
check_not_401 "dvta token    -> /pt/default  (same narrowed config as /v2)"                         "$DVTA" "$(printf "$PROBE_PATH_TEMPLATE" default)"
echo

echo "=== /v2 ≡ /physical-tenants/default identity (both derive from forPhysicalTenant(default)) ==="
check_same "default token  (rejected on both)" "$DEF"
check_same "dvta token     (accepted on both)" "$DVTA"
echo

rm -f /tmp/pt-poc-api-body
echo "=== Results: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -gt 0 ]] && exit 1 || exit 0
