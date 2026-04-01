#!/usr/bin/env bash
set -euo pipefail

# Required env:
# - GH_TOKEN
# - TARGET_REPO
# - WORKFLOW_FILE
#
# Required for discovery (unless RUN_ID is provided):
# - CORRELATION_ID
#
# Optional:
# - RUN_ID (if already known, discovery is skipped)
# - GITHUB_OUTPUT (for downstream_run_url output)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=gh-api-with-retry.sh
source "$SCRIPT_DIR/gh-api-with-retry.sh"

if [[ -z "${RUN_ID:-}" ]]; then
  if [[ -z "${CORRELATION_ID:-}" ]]; then
    echo "RUN_ID not provided and CORRELATION_ID is empty; cannot discover downstream run."
    exit 1
  fi

  echo "Discovering downstream run for correlation id: $CORRELATION_ID"
  echo "Target repo: $TARGET_REPO"
  echo "Workflow file: $WORKFLOW_FILE"

  # Try up to ~40 minutes to discover a run (80 * 30s)
  for _ in {1..80}; do
    RUN_ID="$(
      gh_api_with_retry "/repos/$TARGET_REPO/actions/workflows/$WORKFLOW_FILE/runs?event=repository_dispatch&per_page=50" \
      | jq -r --arg c "$CORRELATION_ID" '
          .workflow_runs
          | map(select(.display_title != null and (.display_title | contains($c))))
          | sort_by(.created_at)
          | last
          | .id // empty
        '
    )"

    if [[ -z "$RUN_ID" ]]; then
      echo "No downstream run found yet for correlation id. Sleeping..."
      sleep 30
      continue
    fi

    echo "Locked onto downstream run id: $RUN_ID"

    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
      echo "downstream_run_url=https://github.com/$TARGET_REPO/actions/runs/$RUN_ID" >> "$GITHUB_OUTPUT"
    fi

    break
  done

  if [[ -z "${RUN_ID:-}" ]]; then
    echo "Timed out discovering downstream run id for correlation id: $CORRELATION_ID"
    exit 1
  fi
fi

echo "Waiting for downstream run to complete: https://github.com/$TARGET_REPO/actions/runs/$RUN_ID"

# 110 iterations × 30s sleep ≈ 55 minutes
for _ in {1..110}; do
  RUN_JSON="$(gh_api_with_retry "/repos/$TARGET_REPO/actions/runs/$RUN_ID")"
  STATUS="$(jq -r .status <<<"$RUN_JSON")"
  CONCLUSION="$(jq -r .conclusion <<<"$RUN_JSON")"

  echo "Downstream status: $STATUS ($CONCLUSION)"

  if [[ "$STATUS" == "completed" ]]; then
    if [[ "$CONCLUSION" == "success" ]]; then
      echo "Downstream workflow succeeded!"
      exit 0
    fi
    echo "Downstream concluded as: $CONCLUSION"
    exit 1
  fi

  sleep 30
done

echo "Timed out waiting for downstream workflow to complete."
exit 1