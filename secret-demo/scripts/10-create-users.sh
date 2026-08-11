#!/usr/bin/env bash
# Scene 8a - three non-admin users, none of them holding any SECRET grant yet.
#
#   noGrantUser  - stays without grants, to show the default deny
#   revealUser   - will get SECRET:REVEAL on one single reference
#   listUser     - will get SECRET:READ on "*"
#
# The demo admin needs no grant: the default admin role already carries SECRET:READ and
# SECRET:REVEAL on "*".

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

for username in noGrantUser revealUser listUser; do
  echo "== $username =="
  api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/users "$(cat <<JSON
{
  "username": "$username",
  "password": "$DEMO_PASSWORD",
  "name": "$username",
  "email": "$username@example.com"
}
JSON
)"
done
