#!/usr/bin/env bash
# Scene 7c - resolve the incident, which retries the parked job.
#
# Resolving a SECRET_RESOLUTION_ERROR incident makes the parked job activatable again. The next
# activation attempts resolution once more, and now that the file exists it succeeds and the value
# is injected. Equivalent to pressing "Retry" on the incident in Operate.
#
# Usage: 09-resolve-incident.sh <incidentKey>

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [[ $# -lt 1 ]]; then
  echo "usage: $(basename "$0") <incidentKey>" >&2
  exit 1
fi

curl -sS -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -w 'HTTP %{http_code}\n' \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/v2/incidents/$1/resolution"
