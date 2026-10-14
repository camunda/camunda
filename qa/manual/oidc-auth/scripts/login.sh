#!/usr/bin/env bash
# login.sh <tenant or ""> <registration-id>: logs in as alice with the authorization code flow and
# prints the username that Camunda resolved. Use it for a fast check; use a real browser to check
# the web apps.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
wait_ready

prefix=""
[ -n "$1" ] && prefix="/physical-tenants/$1"
jars="$(mktemp -d)"

authorize="$(curl -s -o /dev/null -w '%{redirect_url}' -c "$jars/app" -b "$jars/app" \
  -H 'Accept: text/html' "$CAMUNDA$prefix/oauth2/authorization/$2")"
form="$(curl -s -c "$jars/idp" -b "$jars/idp" "$authorize" | grep -oE 'action="[^"]+"' | head -1 |
  sed -E 's/action="([^"]+)"/\1/; s/&amp;/\&/g')"
callback="$(curl -s -o /dev/null -w '%{redirect_url}' -c "$jars/idp" -b "$jars/idp" \
  --data 'username=alice&password=alice' "$form")"
curl -s -o /dev/null -c "$jars/app" -b "$jars/app" "$callback"
curl -s -b "$jars/app" -H 'Accept: application/json' "$CAMUNDA$prefix/v2/authentication/me" |
  sed -E 's/.*"username":"([^"]+)".*/\1/'
