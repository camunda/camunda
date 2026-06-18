#!/usr/bin/env bash
# INTERIM — drop before review. Scenario D (full matrix) of the PT API smoke harness.
#
# Comprehensive per-physical-tenant isolation matrix across ALL THREE PTs (default, tenanta,
# tenantb). Each tenant's token is probed against its OWN PT path and EVERY other PT path, so the
# output reads as one row per token. It demonstrates four things:
#
#   1. OWN-CHAIN ACCEPTANCE     — each PT accepts a token carrying its own provider's audience.
#   2. IdP (ISSUER) ISOLATION   — a token from a different Keycloak realm is rejected (the chain
#                                 doesn't trust that issuer), e.g. the default-realm token (:8081)
#                                 on /pt/tenanta or /pt/tenantb.
#   3. AUDIENCE ISOLATION on a SHARED IdP — `default` and `tenanta` both use the tenanta realm
#                                 (:8082). Only the token's `aud` claim decides which PT accepts it
#                                 (pt-default-via-tenanta-aud vs pt-tenanta-aud), so each is rejected
#                                 on the other's path DESPITE the identical issuer.
#   4. SHARED IdP + AUDIENCE WORKS — the tenantb realm+audience (pt-tenantb-aud) is accepted on BOTH
#                                 /pt/tenantb (its own) AND /pt/default (default inherits the tenantb
#                                 cluster provider): same issuer AND same audience serving two PTs,
#                                 isolated only by path.
#
# Probe: GET /physical-tenants/<id>/v2/authentication/me.
# Requires: ./pt-smoke-test-idp.sh and ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-two-tenants.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_DEFAULT="http://localhost:8081/realms/default/protocol/openid-connect/token"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"
KC_TENANTB="http://localhost:8083/realms/tenantb/protocol/openid-connect/token"

PROBE_PATH_TEMPLATE="/physical-tenants/%s/v2/authentication/me"

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
    curl -sS -o /tmp/pt-smoke-test-api-body -w "%{http_code}" -H "Authorization: Bearer $tok" "$OC$path"
  else
    curl -sS -o /tmp/pt-smoke-test-api-body -w "%{http_code}" "$OC$path"
  fi
}

# check_not_401: token accepted by the chain (200, or 403 from authz — anything but 401).
check_not_401() {
  local label="$1" tok="$2" pt="$3" status
  status=$(_status "$tok" "$(printf "$PROBE_PATH_TEMPLATE" "$pt")")
  printf "%-86s %s  " "$label" "$status"
  if [[ "$status" != "401" ]]; then echo "PASS (accepted)"; PASS=$((PASS + 1))
  else echo "FAIL (got 401 — token rejected)"; cat /tmp/pt-smoke-test-api-body 2>/dev/null && echo; FAIL=$((FAIL + 1)); fi
}

# check_401: chain rejected the token (or no token) with 401.
check_401() {
  local label="$1" tok="$2" pt="$3" status
  status=$(_status "$tok" "$(printf "$PROBE_PATH_TEMPLATE" "$pt")")
  printf "%-86s %s  " "$label" "$status"
  if [[ "$status" == "401" ]]; then echo "PASS (401 rejected)"; PASS=$((PASS + 1))
  else echo "FAIL (expected 401, got $status)"; cat /tmp/pt-smoke-test-api-body 2>/dev/null && echo; FAIL=$((FAIL + 1)); fi
}

echo "=== Acquiring one token per realm/audience ==="
DEF=$(token  "$KC_DEFAULT" camunda-pt-default-client             default-secret             alice alice)
TA=$(token   "$KC_TENANTA" camunda-pt-tenanta-client             tenanta-secret             bob   bob)
# DVTA: same tenanta realm (issuer :8082) as TA, but default's client/audience — drives the
# same-issuer AUDIENCE-isolation cells.
DVTA=$(token "$KC_TENANTA" camunda-pt-default-via-tenanta-client default-via-tenanta-secret bob   bob)
TB=$(token   "$KC_TENANTB" camunda-pt-tenantb-client             tenantb-secret             carol carol)
echo "  default  (alice, :8081, aud pt-default-aud):             ${DEF:0:24}..."
echo "  tenanta  (bob,   :8082, aud pt-tenanta-aud):             ${TA:0:24}..."
echo "  dvta     (bob,   :8082, aud pt-default-via-tenanta-aud): ${DVTA:0:24}..."
echo "  tenantb  (carol, :8083, aud pt-tenantb-aud):             ${TB:0:24}..."
echo

echo "=== default token (pt-default-aud, realm :8081) — own PT only ==="
check_not_401 "default -> /pt/default  (own audience)"                                  "$DEF" default
check_401     "default -> /pt/tenanta  (IdP ISOLATION: realm :8081 not assigned)"       "$DEF" tenanta
check_401     "default -> /pt/tenantb  (IdP ISOLATION: realm :8081 not assigned)"       "$DEF" tenantb
echo

echo "=== tenanta token (pt-tenanta-aud, realm :8082) — own PT only ==="
check_not_401 "tenanta -> /pt/tenanta  (own audience)"                                  "$TA"  tenanta
check_401     "tenanta -> /pt/default  (AUDIENCE ISOLATION: same :8082 issuer, private aud)" "$TA" default
check_401     "tenanta -> /pt/tenantb  (different IdP + audience)"                       "$TA"  tenantb
echo

echo "=== dvta token (pt-default-via-tenanta-aud, realm :8082) — default's view of the SHARED tenanta IdP ==="
check_not_401 "dvta    -> /pt/default  (default's tenanta slot — shared :8082 issuer, default's aud)" "$DVTA" default
check_401     "dvta    -> /pt/tenanta  (AUDIENCE ISOLATION: same :8082 issuer as tenanta, wrong aud)" "$DVTA" tenanta
check_401     "dvta    -> /pt/tenantb  (different IdP + audience)"                       "$DVTA" tenantb
echo "    ^ tenanta + dvta share realm :8082 — only the aud claim decides the PT (KEY: audience isolation)."
echo

echo "=== tenantb token (pt-tenantb-aud, realm :8083) — own PT AND default ==="
check_not_401 "tenantb -> /pt/tenantb  (own audience)"                                  "$TB"  tenantb
check_not_401 "tenantb -> /pt/default  (SHARED IdP+AUDIENCE: default inherits tenantb provider)" "$TB" default
check_401     "tenantb -> /pt/tenanta  (different IdP + audience)"                       "$TB"  tenanta
echo "    ^ same realm :8083 + same aud accepted on TWO PTs (tenantb & default) — isolated by path only."
echo

echo "=== unauthenticated — every PT path rejects a missing token ==="
check_401     "no token -> /pt/default"                                                 ""     default
check_401     "no token -> /pt/tenanta"                                                 ""     tenanta
check_401     "no token -> /pt/tenantb"                                                 ""     tenantb
echo

rm -f /tmp/pt-smoke-test-api-body

echo "=== Results: $PASS passed, $FAIL failed ==="
if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
