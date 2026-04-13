#!/usr/bin/env bash
# Show the Playwright HTML report and list all available trace files after a test run.
#
# Usage:
#   ./show-failures.sh
#
# Requires:
#   - Node.js and npm
#   - A completed test run (run-tests.sh must have been executed first)
#   - REPO_ROOT env var, or the script auto-detects the repo root from its path
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
SUITE_DIR="$REPO_ROOT/qa/c8-orchestration-cluster-e2e-test-suite"

echo "=== Trace files from last test run ==="
find "$SUITE_DIR/test-results" -name "trace.zip" 2>/dev/null | while read -r trace; do
  echo "  $trace"
  echo "  → view: npx playwright show-trace \"$trace\""
done

echo ""
echo "=== Opening HTML report ==="
cd "$SUITE_DIR"
npx playwright show-report html-report
