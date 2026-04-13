#!/usr/bin/env bash
# Stop the local Camunda 8 E2E test environment.
#
# Usage:
#   ./stop-environment.sh
#
# Requires:
#   - Docker and Docker Compose
#   - REPO_ROOT env var, or the script auto-detects the repo root from its path
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
COMPOSE_DIR="$REPO_ROOT/qa/c8-orchestration-cluster-e2e-test-suite/config"

echo "Stopping E2E test environment..."
cd "$COMPOSE_DIR"
DATABASE=elasticsearch docker compose down

echo "Environment stopped ✓"
