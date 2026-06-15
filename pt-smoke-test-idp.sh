#!/usr/bin/env bash
#
# INTERIM — drop before review. Part of the local PT API smoke harness.
#
# Boots two Keycloak realms and one Elasticsearch node for the physical-tenant API smoke
# harness:
#   - "default" realm on :8081  (issuer: http://localhost:8081/realms/default)
#   - "tenanta" realm on :8082  (issuer: http://localhost:8082/realms/tenanta)
#   - Elasticsearch on :9200    (single-node, no auth, no TLS)
#
# Elasticsearch is required because PhysicalTenantSearchClientReadersConfiguration is
# @ConditionalOnSecondaryStorageType(elasticsearch, opensearch) — with rdbms as secondary
# storage the multi-PT search readers do not activate and OC fails to start with more than
# one physical tenant ("Unknown physical tenant: 'tenanta'. Known tenants: [default]").
#
# Requires Docker (with enough memory for ES — at least 4 GB recommended).
# First invocation pulls the Keycloak and ES images; subsequent runs start in ~20s.
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
ES_IMAGE="docker.elastic.co/elasticsearch/elasticsearch:8.19.13"

cleanup() {
  echo
  echo "Stopping containers..."
  docker rm -f pt-smoke-test-kc-default pt-smoke-test-kc-tenanta pt-smoke-test-kc-tenantb pt-smoke-test-es 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Remove any leftover containers from a previous run
docker rm -f pt-smoke-test-kc-default pt-smoke-test-kc-tenanta pt-smoke-test-kc-tenantb pt-smoke-test-es 2>/dev/null || true

echo "Starting Elasticsearch (port 9200)..."
# Disable the disk-based shard allocation decider. On Colima/Docker-Desktop the ES container
# sees the (small, often nearly-full) VM disk, so the flood-stage watermark trips and ES marks
# all indices read-only — a false positive for this local smoke harness. Disabling the decider
# avoids the spurious read-only block; this is a throwaway single-node dev node, never production.
docker run -d \
  --name pt-smoke-test-es \
  -p 9200:9200 \
  -e discovery.type=single-node \
  -e xpack.security.enabled=false \
  -e "cluster.routing.allocation.disk.threshold_enabled=false" \
  -e "ES_JAVA_OPTS=-Xms1g -Xmx1g" \
  "$ES_IMAGE"

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

echo "Waiting for Elasticsearch to become ready..."
for i in $(seq 1 60); do
  STATUS=$(curl -fsS "http://localhost:9200/_cluster/health" 2>/dev/null \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || true)
  if [ "$STATUS" = "green" ] || [ "$STATUS" = "yellow" ]; then
    echo "  pt-smoke-test-es ready (cluster status: $STATUS)"
    # Safety net (in addition to the disk.threshold_enabled=false start-up setting): disable the
    # disk decider via the cluster settings API and clear any flood-stage read-only block, in case
    # the Colima/Docker VM disk already tripped the watermark on a reused volume. Idempotent.
    curl -fsS -X PUT "http://localhost:9200/_cluster/settings" \
      -H 'Content-Type: application/json' \
      -d '{"persistent":{"cluster.routing.allocation.disk.threshold_enabled":false}}' \
      >/dev/null 2>&1 || true
    curl -fsS -X PUT "http://localhost:9200/_all/_settings" \
      -H 'Content-Type: application/json' \
      -d '{"index.blocks.read_only_allow_delete":null}' >/dev/null 2>&1 || true
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "  ERROR: pt-smoke-test-es did not become ready within 120s" >&2
    exit 1
  fi
  sleep 2
done

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
echo "=== PT smoke-test local IdPs + ES ready ==="
echo "Elasticsearch:    http://localhost:9200"
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
docker wait pt-smoke-test-kc-default pt-smoke-test-kc-tenanta pt-smoke-test-es >/dev/null 2>&1 &
wait $! || true
