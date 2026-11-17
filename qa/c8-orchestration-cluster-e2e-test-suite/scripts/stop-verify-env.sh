#!/usr/bin/env bash
set -euo pipefail

# Tears down whatever start-verify-env.sh brought up. Safe to call even if
# nothing is running (all steps are best-effort).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUITE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SUITE_DIR}/../.." && pwd)"
CONFIG_DIR="${SUITE_DIR}/config"

if [ -f "${REPO_ROOT}/dev-dist/camunda.pid" ]; then
  pid=$(cat "${REPO_ROOT}/dev-dist/camunda.pid")
  echo "Stopping H2/RDBMS Camunda process (PID ${pid})..."
  kill "${pid}" 2>/dev/null || true
  rm -f "${REPO_ROOT}/dev-dist/camunda.pid"
fi

( cd "${CONFIG_DIR}" && DATABASE=elasticsearch docker compose down -v 2>/dev/null ) || true

rm -f "${SUITE_DIR}/.env"
