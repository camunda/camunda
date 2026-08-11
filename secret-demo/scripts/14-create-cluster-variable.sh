#!/usr/bin/env bash
# Scene 11a - a cluster variable that holds a secret reference.
#
# kind: SECRET_REFERENCE is what allows camunda.secrets.* references inside the value. The model
# then names no secret at all: it reads camunda.vars.env.slackConfig.token, and the engine folds
# the reference the cluster variable carries onto the job at creation. This is the shape the
# Credentials Manager epic stores a credential in.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/cluster-variables/global "$(cat <<'JSON'
{
  "name": "slackConfig",
  "kind": "SECRET_REFERENCE",
  "value": {
    "url": "https://hooks.slack.example/T0/B0",
    "token": "camunda.secrets.apiToken"
  },
  "metadata": {
    "kind": "CREDENTIAL",
    "displayName": "Slack - production"
  }
}
JSON
)"
