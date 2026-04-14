#!/usr/bin/env bash
set -euo pipefail

# Required env:
# - GH_TOKEN
# - TARGET_REPO
# - WORKFLOW_FILE (filename only)
# - SINCE_TIME
#
# Outputs (via GITHUB_OUTPUT):
# - run_id
# - run_url

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=gh-api-with-retry.sh
source "$SCRIPT_DIR/gh-api-with-retry.sh"

echo "Discovering downstream run in $TARGET_REPO ($WORKFLOW_FILE)"
echo "Looking for runs created since: $SINCE_TIME"

RUN_ID=""

for _ in {1..90}; do
  # Use pagination so we don't miss the relevant run if it falls off the first page.
  # We intentionally do not use gh_api_with_retry here because `gh api --paginate`
  # streams multiple JSON documents; transient failures will fail the step and the
  # loop will retry on the next iteration.
  RUN_ID="$(
    gh api --paginate \
      -H "Accept: application/vnd.github+json" \
      "/repos/$TARGET_REPO/actions/workflows/$WORKFLOW_FILE/runs?event=repository_dispatch&per_page=100" \
    | jq -s -r --arg t "$SINCE_TIME" '
        # jq -s "slurps" all JSON docs (one per page) into an array.
        map(.workflow_runs[]?)
        | map(select(.created_at >= $t))
        | sort_by(.created_at)
        | last
        | .id // empty
      '
  )"

  if [[ -n "$RUN_ID" ]]; then
    echo "Found downstream run id: $RUN_ID"
    echo "run_id=$RUN_ID" >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
    echo "run_url=https://github.com/$TARGET_REPO/actions/runs/$RUN_ID" >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
    exit 0
  fi

  echo "Downstream run not visible yet. Sleeping..."
  sleep 10
done

echo "Timed out discovering downstream run id."
exit 1
