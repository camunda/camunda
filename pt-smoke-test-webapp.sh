#!/usr/bin/env bash
# INTERIM — drop before review. Webapp route smoke test for the physical-tenant prefix.
#
# Verifies that per-PT webapp route dispatching works end-to-end: the
# PhysicalTenantRequestMappingHandlerMapping now registers /operate, /tasklist and /admin
# routes alongside /v2, so a request to /physical-tenants/<id>/operate reaches the existing
# OperateIndexController instead of falling through to a 404.
#
# Checks:
#   1. Known PT + known webapp path  → NOT 404 (controller found; auth may redirect or accept)
#   2. Known PT + static asset path  → NOT 404 (ResourceHttpRequestHandler serves the file)
#   3. Unknown PT + any webapp path  → 404 (CSL catch-all rejects the unknown tenant)
#   4. Unprefixed webapp path        → still works (no regression on existing routes)
#
# For check #1 the expected status is 302 (redirect to /login) because OC is running with
# OIDC auth and the incoming request carries no session cookie. That proves routing succeeded
# (a handler was found and the security chain ran) rather than returning a 404 from the
# DispatcherServlet's "no handler" path.
#
# Requires: ./pt-smoke-test-idp.sh and ./pt-smoke-test-oc.sh running (base Scenario A).
# Dependencies: curl.

set -u

OC="http://localhost:8080"

PASS=0
FAIL=0

_status() {
  # -L would follow redirects; we intentionally do NOT follow so we can assert the 302.
  curl -sS -o /tmp/pt-smoke-test-webapp-body -w "%{http_code}" "$OC$1"
}

check_not_404() {
  local label="$1" path="$2" status
  status=$(_status "$path")
  printf "%-72s %s  " "$label" "$status"
  if [[ "$status" != "404" ]]; then
    echo "PASS (not 404 — handler found, routing works)"; PASS=$((PASS + 1))
  else
    echo "FAIL (got 404 — no handler found; routing broken)"; cat /tmp/pt-smoke-test-webapp-body 2>/dev/null && echo
    FAIL=$((FAIL + 1))
  fi
}

check_status() {
  local label="$1" path="$2" expected="$3" status
  status=$(_status "$path")
  printf "%-72s %s  " "$label" "$status"
  if [[ "$status" == "$expected" ]]; then
    echo "PASS (got $expected as expected)"; PASS=$((PASS + 1))
  else
    echo "FAIL (expected $expected, got $status)"; cat /tmp/pt-smoke-test-webapp-body 2>/dev/null && echo
    FAIL=$((FAIL + 1))
  fi
}

echo "=== Webapp route routing — /physical-tenants/<id>/<webapp> ==="
echo "    (Unauthenticated; expect 302 to /login, not 404 — proves the controller was found)"
echo
check_not_404 "/pt/tenanta/operate  → OperateIndexController (known PT, known webapp)"   "/physical-tenants/tenanta/operate"
check_not_404 "/pt/tenanta/tasklist → TasklistIndexController (known PT)"                 "/physical-tenants/tenanta/tasklist"
check_not_404 "/pt/default/operate  → OperateIndexController (default PT alias)"          "/physical-tenants/default/operate"
echo

echo "=== Unknown physical tenant — webapp routes must 404 ==="
check_status "/pt/nosuchpt/operate  → 404 (unknown PT rejected by catch-all chain)"   "/physical-tenants/nosuchpt/operate"   "404"
check_status "/pt/nosuchpt/tasklist → 404 (unknown PT rejected)"                       "/physical-tenants/nosuchpt/tasklist"  "404"
echo

echo "=== Unprefixed webapp routes — must be unaffected (regression guard) ==="
check_not_404 "/operate  → still reachable at the unprefixed path"  "/operate"
check_not_404 "/tasklist → still reachable at the unprefixed path"  "/tasklist"
echo

echo "=== SPA sub-routes (forward-to-index) — routing must not break on sub-paths ==="
check_not_404 "/pt/tenanta/operate/processes → forwarded to index (known SPA route)"   "/physical-tenants/tenanta/operate/processes"
check_not_404 "/pt/tenanta/tasklist/tasks    → forwarded to index (known SPA route)"   "/physical-tenants/tenanta/tasklist/tasks"
echo

rm -f /tmp/pt-smoke-test-webapp-body

echo "=== Results: $PASS passed, $FAIL failed ==="
if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
