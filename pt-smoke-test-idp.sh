#!/usr/bin/env bash
#
# INTERIM — drop before review. Part of the local PT API smoke harness.
#
# Boots three Keycloak realms for the physical-tenant API smoke harness:
#   - "default" realm on :8081  (issuer: http://localhost:8081/realms/default)
#   - "tenanta" realm on :8082  (issuer: http://localhost:8082/realms/tenanta)
#   - "tenantb" realm on :8083  (issuer: http://localhost:8083/realms/tenantb)
#
# Secondary storage is in-memory H2 (RDBMS), configured per physical tenant in the
# application-pt-smoke-test*.yaml profiles — no external datastore is needed, so this
# harness only provides the IdPs.
#
# Requires Docker. First invocation pulls the Keycloak image; subsequent runs start in ~20s.
# Leave this running while ./pt-smoke-test-oc.sh and ./pt-smoke-test-api.sh are active.
# Press Ctrl-C to stop all containers.
#
# Usage:
#   ./pt-smoke-test-idp.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REALM_DIR="$SCRIPT_DIR/dist/src/test/resources/pt-smoke-test"

KC_IMAGE="quay.io/keycloak/keycloak:26.2"
KC_OPTS="start-dev --health-enabled=true"

cleanup() {
  echo
  echo "Stopping containers..."
  docker rm -f pt-smoke-test-kc-default pt-smoke-test-kc-tenanta pt-smoke-test-kc-tenantb 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Remove any leftover containers from a previous run
docker rm -f pt-smoke-test-kc-default pt-smoke-test-kc-tenanta pt-smoke-test-kc-tenantb 2>/dev/null || true

echo "Starting default realm (port 8081)..."
docker run -d \
  --name pt-smoke-test-kc-default \
  -p 8081:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v "$REALM_DIR/default-realm.json:/opt/keycloak/data/import/default-realm.json:ro" \
  "$KC_IMAGE" \
  start-dev --import-realm --health-enabled=true

echo "Starting tenanta realm (port 8082)..."
docker run -d \
  --name pt-smoke-test-kc-tenanta \
  -p 8082:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v "$REALM_DIR/tenanta-realm.json:/opt/keycloak/data/import/tenanta-realm.json:ro" \
  "$KC_IMAGE" \
  start-dev --import-realm --health-enabled=true

echo "Starting tenantb realm (port 8083)..."
docker run -d \
  --name pt-smoke-test-kc-tenantb \
  -p 8083:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v "$REALM_DIR/tenantb-realm.json:/opt/keycloak/data/import/tenantb-realm.json:ro" \
  "$KC_IMAGE" \
  start-dev --import-realm --health-enabled=true

echo "Waiting for Keycloak realms to become ready..."
# Keycloak 26 serves /health on the management port (9000), not the app port, so we
# probe the realm's OIDC discovery document on the app port instead — this confirms both
# that Keycloak is up AND that the realm imported successfully.
for entry in "pt-smoke-test-kc-default:8081:default" "pt-smoke-test-kc-tenanta:8082:tenanta" "pt-smoke-test-kc-tenantb:8083:tenantb"; do
  IFS=: read -r container port realm <<<"$entry"
  for i in $(seq 1 60); do
    if curl -fsS "http://localhost:$port/realms/$realm/.well-known/openid-configuration" >/dev/null 2>&1; then
      echo "  $container ready (realm '$realm' on port $port)"
      break
    fi
    if [ "$i" -eq 60 ]; then
      echo "  ERROR: $container did not become ready within 120s" >&2
      exit 1
    fi
    sleep 2
  done
done

echo
echo "=== PT smoke-test local IdPs ready ==="
echo "default issuer:   http://localhost:8081/realms/default"
echo "tenanta issuer:   http://localhost:8082/realms/tenanta"
echo "tenantb issuer:   http://localhost:8083/realms/tenantb   (Scenario D — two non-default tenants)"
echo
echo "Clients:"
echo "  camunda-pt-default-client / default-secret          (default realm)"
echo "  camunda-pt-tenanta-client / tenanta-secret          (tenanta realm)"
echo "  camunda-pt-default-via-tenanta-client / default-via-tenanta-secret  (tenanta realm)"
echo "  camunda-pt-tenantb-client / tenantb-secret          (tenantb realm)"
echo
echo "Users:"
echo "  alice / alice  (default realm)"
echo "  bob   / bob    (tenanta realm)"
echo "  carol / carol  (tenantb realm)"
echo
echo "Press Ctrl-C to stop."
# Block until the containers stop (or Ctrl-C). Notes:
#  - A bare `wait` returns immediately: `docker run -d` containers are not shell jobs.
#  - Blocking on `docker wait` in the FOREGROUND would defer the INT/TERM trap until that
#    command returns, so Ctrl-C wouldn't clean up promptly.
# Running `docker wait` in the background and blocking on the `wait` BUILTIN fixes both:
# the builtin is interruptible by the trap, so Ctrl-C tears the containers down at once.
docker wait pt-smoke-test-kc-default pt-smoke-test-kc-tenanta pt-smoke-test-kc-tenantb >/dev/null 2>&1 &
wait $! || true
