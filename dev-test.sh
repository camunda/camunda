#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------
# dev-test.sh – Run QA E2E v2 API tests against a running server.
#
# Usage:
#   ./dev-test.sh                       Run all v2 API tests
#   ./dev-test.sh incident             Run tests matching a grep filter
#   ./dev-test.sh tests/api/v2/incident/ Run tests in a specific directory
#   ./dev-test.sh tests/api/v2/incident/incident-search-api.spec.ts  Run a specific file
#
# Anything that looks like a path (contains / or .spec.) is passed
# as a file filter; otherwise it's used as a --grep pattern.
#
# Expects Camunda running at http://localhost:8080 (use dev-start.sh)
# ------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_DIR="$SCRIPT_DIR/qa/c8-orchestration-cluster-e2e-test-suite"

# Check server is up
if ! curl -sf -u demo:demo http://localhost:8080/v2/topology >/dev/null 2>&1; then
  echo "ERROR: Camunda is not running at http://localhost:8080"
  echo "Start it first with: ./dev-start.sh --skip-build"
  exit 1
fi

cd "$TEST_DIR"

# Install deps if needed
if [ ! -d "node_modules" ]; then
  echo "=== Installing test dependencies ==="
  npm install
  npx playwright install chromium
fi

# Build test args — run only v2 API tests
ARGS=(--project=api-tests)
if [ $# -gt 0 ]; then
  if [[ "$1" == */* || "$1" == *.spec.* ]]; then
    # Looks like a file path or glob — use as test file filter
    ARGS+=("$1")
  else
    # Use as a grep pattern for test names
    ARGS+=(tests/api/v2/ --grep "$1")
  fi
else
  ARGS+=(tests/api/v2/)
fi

echo "=== Running v2 API tests ==="

CORE_APPLICATION_URL="http://localhost:8080" \
ZEEBE_REST_ADDRESS="http://localhost:8080" \
CAMUNDA_AUTH_STRATEGY="BASIC" \
CAMUNDA_BASIC_AUTH_USERNAME="demo" \
CAMUNDA_BASIC_AUTH_PASSWORD="demo" \
CAMUNDA_TASKLIST_V2_MODE_ENABLED="false" \
LOCAL_TEST="true" \
npx playwright test "${ARGS[@]}"
