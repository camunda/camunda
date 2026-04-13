#!/usr/bin/env bash
# Run Playwright E2E tests for the Camunda 8 orchestration cluster.
#
# Usage:
#   ./run-tests.sh                                         # Full suite (chromium)
#   ./run-tests.sh --project=api-tests                     # API tests only
#   ./run-tests.sh --project=chromium                      # UI tests, chromium
#   ./run-tests.sh tests/tasklist/task-details.spec.ts     # Single spec file
#   ./run-tests.sh -g "should display task details"        # Filter by test name
#   ./run-tests.sh --ui                                    # Interactive Playwright UI
#
# Requires:
#   - Node.js and npm
#   - A running local environment (use start-environment.sh first)
#   - A .env file in qa/c8-orchestration-cluster-e2e-test-suite/ (see SKILL.md)
#   - REPO_ROOT env var, or the script auto-detects the repo root from its path
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
SUITE_DIR="$REPO_ROOT/qa/c8-orchestration-cluster-e2e-test-suite"

ENV_FILE="$SUITE_DIR/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

export LOCAL_TEST="${LOCAL_TEST:-true}"

cd "$SUITE_DIR"

if [[ $# -eq 0 ]]; then
  npx playwright test --project=chromium
else
  npx playwright test "$@"
fi
