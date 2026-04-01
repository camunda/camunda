#!/usr/bin/env bash
set -euo pipefail

# Writes to $GITHUB_STEP_SUMMARY
if [[ -n "${RUN_URL:-}" ]]; then
  {
    echo "### SaaS Downstream Workflow Run"
    echo ""
    echo "**Dispatch time (UTC):** \`${DISPATCH_TIME:-}\`"
    echo "**Search since (UTC):** \`${SINCE_TIME:-}\`"
    echo ""
    echo "[Open downstream run]($RUN_URL)"
  } >> "$GITHUB_STEP_SUMMARY"
else
  echo "No downstream workflow run URL found." >> "$GITHUB_STEP_SUMMARY"
fi