#!/usr/bin/env bash
# Scene 9 - POST /v2/secrets/resolve, the endpoint inbound connectors use.
#
# Batch-only by design: one round-trip resolves a deduplicated set of up to 20 references. Every
# reference is authorized and resolved independently, so a valid request always answers HTTP 200
# with the outcomes split between "resolved" and "errors":
#
#   ACCESS_DENIED - the caller has no SECRET:REVEAL on that reference
#   NOT_FOUND     - authorized, but no configured store holds it
#
# Usage: 12-resolve-secrets.sh <username> <reference> [reference...]
#        12-resolve-secrets.sh --anonymous <reference>   (expects HTTP 401)

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [[ $# -lt 2 ]]; then
  echo "usage: $(basename "$0") <username|--anonymous> <reference> [reference...]" >&2
  exit 1
fi

user="$1"
shift
refs="$(printf '%s\n' "$@" | jq -R . | jq -s .)"
body="$(jq -n --argjson references "$refs" '{references: $references}')"

if [[ "$user" == "--anonymous" ]]; then
  curl -sS -w '\nHTTP %{http_code}\n' -H 'Content-Type: application/json' \
    -X POST "$BASE_URL/v2/secrets/resolve" -d "$body"
  exit 0
fi

password="$DEMO_PASSWORD"
if [[ "$user" == "$ADMIN_USER" ]]; then
  password="$ADMIN_PASSWORD"
fi

# -D shows the response headers, so Cache-Control: no-store is visible: a response carrying secret
# values must never be cached by a browser or an intermediary proxy.
curl -sS -u "$user:$password" -D /dev/stderr \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/v2/secrets/resolve" -d "$body" | jq .
