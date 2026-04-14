#!/usr/bin/env bash
set -euo pipefail

# Required env:
# - GITHUB_TOKEN
# - TARGET_REPO (owner/repo)
# - EVENT_TYPE
# - SOURCE_REPO
# - SOURCE_SHA
# - CORRELATION_ID
# - C8_VERSION
# - ZEEBE_VERSION
# - OPERATE_VERSION
# - TASKLIST_VERSION
# - CONNECTORS_VERSION
# - OPTIMIZE_VERSION
# - SINCE_MINUTES

DISPATCH_TIME="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
SINCE_TIME="$(date -u -d "$DISPATCH_TIME - ${SINCE_MINUTES} minutes" +"%Y-%m-%dT%H:%M:%SZ")"

echo "dispatch_time=$DISPATCH_TIME" >>"$GITHUB_OUTPUT"
echo "since_time=$SINCE_TIME" >>"$GITHUB_OUTPUT"

echo "Dispatch time: $DISPATCH_TIME"
echo "Search since:  $SINCE_TIME"
echo "Dispatching to: $TARGET_REPO (event_type=$EVENT_TYPE)"

# Build JSON safely (avoids quoting/injection issues)
PAYLOAD="$(
  jq -n \
    --arg event_type "$EVENT_TYPE" \
    --arg correlation_id "$CORRELATION_ID" \
    --arg source_repo "$SOURCE_REPO" \
    --arg source_sha "$SOURCE_SHA" \
    --arg c8Version "$C8_VERSION" \
    --arg zeebeVersion "$ZEEBE_VERSION" \
    --arg operateVersion "$OPERATE_VERSION" \
    --arg tasklistVersion "$TASKLIST_VERSION" \
    --arg connectorsVersion "$CONNECTORS_VERSION" \
    --arg optimizeVersion "$OPTIMIZE_VERSION" \
    '{
      event_type: $event_type,
      client_payload: {
        correlation_id: $correlation_id,
        source_repo: $source_repo,
        source_sha: $source_sha,
        c8Version: $c8Version,
        zeebeVersion: $zeebeVersion,
        operateVersion: $operateVersion,
        tasklistVersion: $tasklistVersion,
        connectorsVersion: $connectorsVersion,
        optimizeVersion: $optimizeVersion
      }
    }'
)"

# Dispatch and fail fast on HTTP errors (prints body on failure)
curl -sS --fail-with-body -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  "https://api.github.com/repos/$TARGET_REPO/dispatches" \
  -d "$PAYLOAD"