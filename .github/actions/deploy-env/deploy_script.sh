#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
# Licensed under a proprietary license. See the License.txt file for more information.
# You may not use this file except in compliance with the proprietary license.
#

#
#!/bin/bash

c7_optimize_deploy_arguments="--dest-namespace ${APP_NAME} \
    --file .ci/deployments-resources/argo/c7-optimize-application.yml \
    --helm-set optimize.image.tag=${DOCKER_TAG} \
    --helm-set cambpm.image.tag=${CAMBPM_VERSION} \
    --helm-set elasticsearch.image.tag=${ES_VERSION} \
    --helm-set global.labels.app=${APP_NAME} \
    --helm-set global.labels.commit=${SHA} \
    --name ${APP_NAME} \
    --project optimize-previews \
    --upsert"

c8_optimize_deploy_arguments="--dest-namespace ${APP_NAME}-c8 \
    --file .ci/deployments-resources/argo/c8-optimize-application.yml \
    --helm-set global.identity.auth.publicIssuerUrl=https://keycloak-stage-c8.optimize.camunda.cloud/auth/realms/camunda-platform
    --helm-set global.identity.auth.optimize.redirectUrl=https://stage-c8.optimize.camunda.cloud
    --helm-set camunda-platform.optimize.image.tag=${DOCKER_TAG} \
    --helm-set camunda-platform.identity.image.tag=${IDENTITY_VERSION} \
    --helm-set camunda-platform.zeebe.image.tag=${ZEEBE_VERSION} \
    --helm-set camunda-platform.zeebe-gateway.image.tag=${ZEEBE_VERSION} \
    --helm-set global.labels.app=c8-${APP_NAME} \
    --helm-set global.labels.commit=${SHA} \
    --name c8-${APP_NAME} \
    --project optimize-previews \
    --auto-prune \
    --upsert"

if [[ "${APP_NAME}" == "optimize-persistent" ]];
then
  argocd app create $c7_optimize_deploy_arguments --revision master --helm-set git.branch=master \
  --helm-set env=persistent
  argocd app sync optimize-persistent --async --force || true
else
  argocd app create $c7_optimize_deploy_arguments  --revision ${REVISION} --helm-set git.branch=${REVISION}
  argocd app create $c8_optimize_deploy_arguments  --revision ${REVISION} --helm-set git.branch=${REVISION}

  argocd app sync optimize-stage c8-optimize-stage --async --force || true
fi
