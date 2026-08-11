#!/usr/bin/env bash
# Scene 4 - a secret reference used as a static value is rejected at deployment.
#
# The input mapping's source is the plain string "camunda.secrets.apiToken" rather than the
# expression "=camunda.secrets.apiToken". Deployment fails with a validation error naming the
# reference, so a reference can never end up embedded in arbitrary text.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

curl -sS -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -w '\nHTTP %{http_code}\n' \
  -F "resources=@$MODELS_DIR/bad-literal-process.bpmn" \
  "$BASE_URL/v2/deployments"
