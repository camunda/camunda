#!/usr/bin/env bash
# Scene 8b - grant a SECRET permission to a user.
#
# The resource id of a SECRET authorization is the whole reference ("camunda.secrets.apiToken"),
# or "*" for every reference. REVEAL authorizes /v2/secrets/resolve, READ authorizes
# /v2/secrets/list. REVEAL does not imply READ.
#
# Usage: 11-grant.sh <username> <REVEAL|READ> <resourceId>
# Example: 11-grant.sh revealUser REVEAL camunda.secrets.apiToken
#          11-grant.sh listUser   READ   '*'

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [[ $# -lt 3 ]]; then
  echo "usage: $(basename "$0") <username> <REVEAL|READ> <resourceId>" >&2
  exit 1
fi

api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/authorizations "$(cat <<JSON
{
  "ownerId": "$1",
  "ownerType": "USER",
  "resourceType": "SECRET",
  "resourceId": "$3",
  "permissionTypes": ["$2"]
}
JSON
)"
