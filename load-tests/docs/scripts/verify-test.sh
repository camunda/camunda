#!/usr/bin/env bash
# Verifies a deployed load test with `loadtestctl verify` and reports the result as a GitHub Actions step output.
set -eo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-test.sh <namespace> [wait_timeout_seconds] [wait_retries] [connectivity_timeout_seconds] [metrics_port]
See `loadtestctl verify --help` for the meaning of the arguments and the PROMETHEUS_* environment variables.
USAGE
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$1" ]]; then
  echo "Error: Missing namespace name." >&2
  usage >&2
  exit 1
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/null}"

args=("$1")
[[ -n "$2" ]] && args+=(--wait-timeout "$2")
[[ -n "$3" ]] && args+=(--wait-retries "$3")
[[ -n "$4" ]] && args+=(--connectivity-timeout "$4")
[[ -n "$5" ]] && args+=(--metrics-port "$5")

# pipefail makes a failing verify fail the script, tee only forwards the status line.
uv run --project "$HERE/../../loadtestctl" loadtestctl verify "${args[@]}" | tee -a "$GITHUB_OUTPUT"
