#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
# Licensed under a proprietary license. See the License.txt file for more information.
# You may not use this file except in compliance with the proprietary license.
#

#
#!/bin/bash

deploy_arguments="--dest-namespace optimize-${APP_NAME} \
    --file .ci/deployments-resources/argo/application.yml \
    --helm-set optimize.image.tag=${DOCKER_TAG} \
    --helm-set cambpm.image.tag=${CAMBPM_VERSION} \
    --helm-set elasticsearch.image.tag=${ES_VERSION} \
    --helm-set global.labels.app=${APP_NAME} \
    --helm-set global.labels.commit=${SHA} \
    --name optimize-${APP_NAME} \
    --project optimize-previews \
    --upsert"


if [[ "${APP_NAME}" == "persistent" ]];
then
  argocd app create $deploy_arguments $persistent_deploy_arguments --revision master --helm-set git.branch=master \
  --helm-set env=persistent
else
  argocd app create $deploy_arguments  --revision ${REVISION} --helm-set git.branch=${REVISION}
fi
