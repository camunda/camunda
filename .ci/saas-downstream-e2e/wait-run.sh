#!/usr/bin/env bash
set -euo pipefail

# Required env:
# - GH_TOKEN
# - TARGET_REPO
# - RUN_ID

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=gh-api-with-retry.sh
source "$SCRIPT_DIR/gh-api-with-retry.sh"

if [[ -z "${RUN_ID:-}" ]]; then
  echo "No RUN_ID provided; discovery step likely failed."
  exit 1
fi

echo "Waiting for downstream run to complete: https://github.com/$TARGET_REPO/actions/runs/$RUN_ID"

# 110 iterations × 30s sleep ≈ 55 minutes, fits within a 60-minute job timeout
for _ in {1..110}; do
  RUN_JSON="$(gh_api_with_retry "/repos/$TARGET_REPO/actions/runs/$RUN_ID")"
  STATUS="$(jq -r .status <<<"$RUN_JSON")"
  CONCLUSION="$(jq -r .conclusion <<<"$RUN_JSON")"

  echo "Downstream status: $STATUS ($CONCLUSION)"

  if [[ "$STATUS" == "completed" ]]; then
    if [[ "$CONCLUSION" == "success" ]]; then
      exit 0
    fi
    echo "Downstream concluded as: $CONCLUSION"
    exit 1
  fi

  sleep 30
done

echo "Timed out waiting for downstream workflow to complete."
exit 1