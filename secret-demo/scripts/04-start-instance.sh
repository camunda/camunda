#!/usr/bin/env bash
# Start a process instance.
# Usage: 04-start-instance.sh [processDefinitionId]   (default: secret-demo-order)

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

PROCESS_ID="${1:-secret-demo-order}"

api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/process-instances \
  "{\"processDefinitionId\": \"$PROCESS_ID\"}"
