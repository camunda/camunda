#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
# Licensed under a proprietary license. See the License.txt file for more information.
# You may not use this file except in compliance with the proprietary license.
#

#
#!/bin/bash

persistent_deploy_arguments="--helm-set env=persistent \
    --helm-set optimize.env.elasticsearchUrl='elasticsearch-es-http:9200' \
    --helm-set cambpm.env.javaOpts='-Xms1g\ -Xmx1g\ -XX:MaxMetaspaceSize=256m\ -Ddb.username=\$DB_USERNAME\ -Ddb.password=\$DB_PASSWORD' \
    --helm-set cambpm.env.postgresUrl='optimize-persistent-postgres.optimize-persistent:5432'"

deploy_arguments="--dest-namespace optimize-${APP_NAME} \
    --file .ci/deployments-resources/argo/application.yml \
    --helm-set optimize.image.tag=${DOCKER_TAG} \
    --helm-set optimize.image.repository=\"gcr.io/ci-30-162810/camunda-optimize\" \
    --helm-set cambpm.image.tag=${CAMBPM_VERSION} \
    --helm-set elasticsearch.image.tag=${ES_VERSION} \
    --helm-set git.branch=${REVISION} \
    --helm-set global.labels.app=${APP_NAME} \
    --helm-set global.labels.commit=${SHA} \
    --name optimize-${APP_NAME} \
    --project optimize-previews \
    --revision ${REVISION} \
    --upsert"


if [[ "${APP_NAME}" == "persistent" ]];
then
  argocd app create $deploy_arguments $persistent_deploy_arguments
else
  argocd app create $deploy_arguments
fi
