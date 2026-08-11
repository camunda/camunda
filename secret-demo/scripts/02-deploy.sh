#!/usr/bin/env bash
# Scene 3 - deploy the demo models.
#
# Deploys the three models that are expected to succeed. The literal-reference model is deployed
# separately by 03-deploy-bad-literal.sh, because it is expected to be rejected.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

curl -sS -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -F "resources=@$MODELS_DIR/order-process.bpmn" \
  -F "resources=@$MODELS_DIR/missing-secret-process.bpmn" \
  -F "resources=@$MODELS_DIR/cluster-variable-process.bpmn" \
  "$BASE_URL/v2/deployments" \
  | jq '.deployments[].processDefinition
        | {processDefinitionId, processDefinitionVersion, processDefinitionKey}'
