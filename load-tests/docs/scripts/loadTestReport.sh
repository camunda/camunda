#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOAD_TEST_REPORT_SCRIPT_DIR="$SCRIPT_DIR" exec go run "$SCRIPT_DIR" "$@"
