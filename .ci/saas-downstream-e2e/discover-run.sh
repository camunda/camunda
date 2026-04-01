#!/usr/bin/env bash
set -euo pipefail

# Required env:
# - GH_TOKEN
# - TARGET_REPO
# - WORKFLOW_FILE (filename only)
# - SINCE_TIME

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=gh-api-with-retry.sh
source "$SCRIPT_DIR/gh-api-with-retry.sh"

echo "Discovering downstream run in $TARGET_REPO ($WORKFLOW_FILE)"
echo "Looking for runs created since: $SINCE_TIME"

RUN_ID=""

for _ in {1..90}; do
  RUN_ID=$(
    gh_api_with_retry "/repos/$TARGET_REPO/actions/workflows/$WORKFLOW_FILE/runs?event=repository_dispatch&per_page=50" \
    | jq -r --arg t "$SINCE_TIME" '
        .workflow_runs
        | map(select(.created_at >= $t))
        | sort_by(.created_at)
        | last
        | .id // empty
      '
  )

  if [[ -n "$RUN_ID" ]]; then
    echo "Found downstream run id: $RUN_ID"
    echo "run_id=$RUN_ID" >> "$GITHUB_OUTPUT"
    echo "run_url=https://github.com/$TARGET_REPO/actions/runs/$RUN_ID" >> "$GITHUB_OUTPUT"
    exit 0
  fi

  echo "Downstream run not visible yet. Sleeping..."
  sleep 10
done

echo "Timed out discovering downstream run id."
exit 1