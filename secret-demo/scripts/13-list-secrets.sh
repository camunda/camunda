#!/usr/bin/env bash
# Scene 10 - POST /v2/secrets/list, the endpoint Modeler autocompletion uses.
#
# Returns names only, never values, and only the references the caller holds SECRET:READ on.
# tls.crt is in the store but never listed: it cannot form a valid camunda.secrets.<name>
# reference, so offering it would only suggest something every expression would reject.
#
# Usage: 13-list-secrets.sh <username>
#        13-list-secrets.sh --anonymous   (expects HTTP 401)

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

user="${1:-$ADMIN_USER}"

if [[ "$user" == "--anonymous" ]]; then
  curl -sS -w '\nHTTP %{http_code}\n' -H 'Content-Type: application/json' \
    -X POST "$BASE_URL/v2/secrets/list" -d '{}'
  exit 0
fi

password="$DEMO_PASSWORD"
if [[ "$user" == "$ADMIN_USER" ]]; then
  password="$ADMIN_PASSWORD"
fi

curl -sS -u "$user:$password" -D /dev/stderr \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/v2/secrets/list" -d '{}' | jq .
