#!/usr/bin/env bash
# Start the local Camunda 8 E2E test environment using Docker Compose.
#
# Usage:
#   ./start-environment.sh        # Tasklist V2 mode (default)
#   ./start-environment.sh v1     # Tasklist V1 mode (legacy)
#
# Requires:
#   - Docker and Docker Compose
#   - REPO_ROOT env var, or the script auto-detects the repo root from its path
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
COMPOSE_DIR="$REPO_ROOT/qa/c8-orchestration-cluster-e2e-test-suite/config"

MODE="${1:-v2}"

if [[ "$MODE" == "v1" ]]; then
  export CAMUNDA_TASKLIST_V2_MODE_ENABLED=false
  echo "Starting environment in Tasklist V1 mode..."
else
  export CAMUNDA_TASKLIST_V2_MODE_ENABLED=true
  echo "Starting environment in Tasklist V2 mode (default)..."
fi

cd "$COMPOSE_DIR"
DATABASE=elasticsearch docker compose up -d camunda elasticsearch

echo ""
echo "Waiting for Camunda to become healthy..."

HEALTH_URL="http://localhost:8080/actuator/health"
TIMEOUT=120
INTERVAL=5
elapsed=0

until curl -sf "$HEALTH_URL" | grep -q '"status":"UP"' 2>/dev/null; do
  if [[ $elapsed -ge $TIMEOUT ]]; then
    echo "ERROR: Camunda did not become healthy within ${TIMEOUT}s."
    echo "Inspect logs with: docker logs camunda --tail 100"
    exit 1
  fi
  echo "  not ready yet (${elapsed}s elapsed)..."
  sleep "$INTERVAL"
  elapsed=$((elapsed + INTERVAL))
done

echo ""
echo "Environment is ready ✓"
echo "  Camunda:       http://localhost:8080"
echo "  Elasticsearch: http://localhost:9200"
