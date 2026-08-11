#!/usr/bin/env bash
# Scene 6 - nothing leaks.
#
# The worker just received the real value, but the variable held in the cluster keeps the
# reference text ("Bearer camunda.secrets.apiToken"). The value is substituted into the
# activation response only, so it is never written to state, exported, or shown in Operate.
#
# Usage: 06-show-stored-variables.sh <processInstanceKey>

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [[ $# -lt 1 ]]; then
  echo "usage: $(basename "$0") <processInstanceKey>" >&2
  exit 1
fi

PROCESS_INSTANCE_KEY="$1"

echo "== variables as stored in the cluster =="
api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/variables/search "$(cat <<JSON
{
  "filter": { "processInstanceKey": "$PROCESS_INSTANCE_KEY" }
}
JSON
)"

echo
echo "== the secret values in the log (expected: no matches) =="
CAMUNDA_LOG_DIR="${CAMUNDA_LOG_DIR:-$DEMO_ROOT/../c8run/log}"
if grep -R -n -e 'sk-live-DEMO-9f3ad41c' -e 'p4ssw0rd-from-the-store' "$CAMUNDA_LOG_DIR" 2>/dev/null; then
  echo "LEAK: a secret value was found in the logs" >&2
  exit 1
fi
echo "no secret value found in the logs"
