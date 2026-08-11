#!/usr/bin/env bash
# Scene 7a - the incident raised when a referenced secret cannot be resolved.
#
# The job is parked in a dedicated waiting state, so no worker ever sees it. Background resolution
# retries with backoff; on exhaustion the engine raises a SECRET_RESOLUTION_ERROR incident on the
# element instance. The same incident is visible in Operate as "Secret resolution error".

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/incidents/search '{
  "filter": { "errorType": "SECRET_RESOLUTION_ERROR" }
}'
