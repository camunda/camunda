#!/usr/bin/env bash
# INTERIM — drop before review. Part of the local PT API smoke harness.
#
# API isolation smoke matrix for the physical-tenant security chains.
#
# Demonstrates the isolation this PR actually delivers under the current (post-`assigned`-removal)
# model, where every physical tenant inherits ALL cluster providers (root ∪ its own overlay):
#
#   - A non-default tenant (e.g. `tenanta`) gets a dedicated API chain at the tenant-first path
#     /physical-tenants/<id>/v2/...  built from its ScopedSecurityDescriptor.
#   - The implicit `default` tenant is the ROOT: it is served at the unprefixed /v2/... cluster path
#     AND, as an alias, at /physical-tenants/default/v2/... — PhysicalTenantScopeProvider emits a
#     default-alias descriptor (built from the root config) whenever PT scoping is active. Both
#     surfaces accept the same (root) providers/audiences.
#   - Per-scope AUDIENCE validation isolates tenants that share one IdP issuer: tenanta's overlay
#     overrides its provider audience to pt-tenanta-aud, so a same-issuer token with a different
#     audience is rejected by tenanta's chain even though the issuer matches.
#
# The probe endpoint is GET .../v2/authentication/me (AuthenticationController, a
# @CamundaRestController — registered under the PT prefix by
# PhysicalTenantRequestMappingHandlerMapping for scoped tenants, and at /v2 for the cluster).
#
# NOT asserted here (deferred): per-tenant ISSUER isolation — rejecting a *different cluster
# provider's* token on a tenant's chain (e.g. the root default-realm token on /pt/tenanta). That
# requires per-tenant provider SELECTION (`providers.assigned`), which is deferred to #54730. With
# all-providers, tenanta inherits the root default `oidc` provider, so it accepts that token. The
# two cells below marked [#54730] print the current behaviour for visibility but do not pass/fail.
#
# Requires: ./pt-poc-idp.sh and ./pt-poc-oc.sh running.
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
    -d "grant_type=password" \
    -d "client_id=$client_id" \
    -d "client_secret=$client_secret" \
    -d "username=$user" \
    -d "password=$pass" \
    | jq -r .access_token
}

_status() {
  local tok="$1" path="$2"
  if [[ -n "$tok" ]]; then
    curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" -H "Authorization: Bearer $tok" "$OC$path"
  else
    curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" "$OC$path"
  fi
}

# check_not_401: token was accepted by the chain (200, or even 403 from authz — anything but 401).
check_not_401() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-72s %s  " "$label" "$status"
  if [[ "$status" != "401" ]]; then
    echo "PASS (not 401)"; PASS=$((PASS + 1))
  else
    echo "FAIL (got 401 — token rejected by chain)"; cat /tmp/pt-poc-api-body 2>/dev/null && echo
    FAIL=$((FAIL + 1))
  fi
}

# check_401: chain rejected the token (or no token) with 401.
check_401() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-72s %s  " "$label" "$status"
  if [[ "$status" == "401" ]]; then
    echo "PASS (401 as expected)"; PASS=$((PASS + 1))
  else
    echo "FAIL (expected 401, got $status)"; cat /tmp/pt-poc-api-body 2>/dev/null && echo
    FAIL=$((FAIL + 1))
  fi
}

# info: print current behaviour without pass/fail (for documented, deferred behaviour).
info() {
  local label="$1" tok="$2" path="$3" status
  status=$(_status "$tok" "$path")
  printf "%-72s %s  INFO\n" "$label" "$status"
}

echo "=== Acquiring tokens ==="
DEF=$(token "$KC_DEFAULT" camunda-pt-default-client default-secret alice alice)
TA=$(token "$KC_TENANTA" camunda-pt-tenanta-client tenanta-secret bob bob)
# DVTA: a token from the SHARED tenanta realm (same issuer :8082 as TA) but a different
# client/audience (pt-default-via-tenanta-aud vs pt-tenanta-aud) — drives the audience-isolation cell.
DVTA=$(token "$KC_TENANTA" camunda-pt-default-via-tenanta-client default-via-tenanta-secret bob bob)
echo "  default token (alice, :8081, aud=pt-default-aud):                ${DEF:0:32}..."
echo "  tenanta token (bob,   :8082, aud=pt-tenanta-aud):                ${TA:0:32}..."
echo "  default-via-tenanta   (bob,   :8082, aud=pt-default-via-tenanta-aud): ${DVTA:0:32}..."
echo

echo "=== Scoped tenant chain — /physical-tenants/tenanta/v2/... ==="
check_not_401 "tenanta token -> /pt/tenanta  (own chain accepts)"               "$TA"   "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
check_401     "no token      -> /pt/tenanta  (unauthenticated rejected)"         ""      "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
check_401     "dvta token    -> /pt/tenanta  (AUDIENCE ISOLATION, shared issuer)" "$DVTA" "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
echo "    ^ KEY CELL: same issuer (:8082) as tenanta, but aud=pt-default-via-tenanta-aud != the"
echo "      tenanta chain's pt-tenanta-aud, so CSL's per-scope audience validator rejects it."
echo

echo "=== Cluster / root-tenant surface — unprefixed /v2/... (the 'default' tenant) ==="
check_not_401 "default token -> /v2  (root-tenant surface accepts default realm)" "$DEF"  "$PROBE_CLUSTER"
check_401     "no token      -> /v2  (unauthenticated rejected)"                   ""      "$PROBE_CLUSTER"
check_401     "tenanta token -> /v2  (scope-private client not in cluster providers)" "$TA" "$PROBE_CLUSTER"
check_not_401 "dvta token    -> /v2  (accepted via root tenanta provider)"          "$DVTA" "$PROBE_CLUSTER"
echo

echo "=== Default-tenant alias — /physical-tenants/default/v2/... (mirrors the cluster surface) ==="
check_not_401 "default token -> /pt/default  (alias accepts default realm)"          "$DEF"  "$(printf "$PROBE_PATH_TEMPLATE" default)"
check_401     "no token      -> /pt/default  (unauthenticated rejected)"              ""      "$(printf "$PROBE_PATH_TEMPLATE" default)"
check_401     "tenanta token -> /pt/default  (scope-private client not in root providers)" "$TA" "$(printf "$PROBE_PATH_TEMPLATE" default)"
check_not_401 "dvta token    -> /pt/default  (accepted via root tenanta provider)"   "$DVTA" "$(printf "$PROBE_PATH_TEMPLATE" default)"
echo

echo "=== Deferred to #54730 (per-tenant ISSUER selection via providers.assigned) — informational ==="
echo "    With all-providers, tenanta inherits the root default 'oidc' provider, so it accepts the"
echo "    default-realm token (cross-issuer); this becomes a 401 once #54730's provider selection lands."
info "default token -> /pt/tenanta  (cross-issuer; 200 today, 401 once #54730 lands)" "$DEF" "$(printf "$PROBE_PATH_TEMPLATE" tenanta)"
echo

rm -f /tmp/pt-poc-api-body

echo "=== Results: $PASS passed, $FAIL failed (informational cells excluded) ==="
if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
