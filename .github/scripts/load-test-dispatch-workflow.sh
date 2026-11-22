#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# The run is found by its title (the `run-name` of the workflow): the dispatch API returns the run ID
# only on recent versions of the gh CLI.

set -euo pipefail

workflow="$1"
ref="$2"
title="$3"
shift 3

# A margin absorbs the clock difference between the runner and GitHub.
started_at="$(date -u -d '30 seconds ago' +%Y-%m-%dT%H:%M:%SZ)"

fields=()
for input in "$@"; do
  fields+=(-f "${input}")
done

gh workflow run "${workflow}" --ref "${ref}" "${fields[@]}"

for ((attempt = 1; attempt <= ${POLL_ATTEMPTS:-30}; attempt++)); do
  run_id="$(gh run list --workflow "${workflow}" --event workflow_dispatch --branch "${ref}" \
    --json databaseId,displayTitle,createdAt \
    | jq -r --arg title "${title}" --arg started_at "${started_at}" \
      '[.[] | select(.displayTitle == $title and .createdAt >= $started_at)] | max_by(.createdAt) | .databaseId // empty')"
  if [[ -n "${run_id}" ]]; then
    echo "Dispatched run ${run_id}: https://github.com/${GITHUB_REPOSITORY:-}/actions/runs/${run_id}"
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
      echo "run-id=${run_id}" >> "${GITHUB_OUTPUT}"
    fi
    exit 0
  fi
  sleep "${POLL_INTERVAL:-10}"
done

echo "::error::No run titled '${title}' appeared for ${workflow} at ${ref}" >&2
exit 1
