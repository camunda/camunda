#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
# Licensed under a proprietary license. See the License.txt file for more information.
# You may not use this file except in compliance with the proprietary license.
#

#
#!/bin/bash

persistent_deploy_arguments (){
  echo '--helm-set env=persistent \
    --helm-set optimize.env.elasticsearchUrl="elasticsearch-es-http:9200" \
    --helm-set cambpm.env.javaOpts="-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m -Ddb.username=$DB_USERNAME -Ddb.password=$DB_PASSWORD" \
    --helm-set cambpm.env.postgresUrl="optimize-persistent-postgres.optimize-persistent:5432"'
}

deploy_arguments() {
  echo '--dest-namespace optimize-${{ env.APP_NAME }} \
    --file .ci/deployments-resources/argo/application.yml \
    --helm-set optimize.image.tag=${{ inputs.docker_tag }} \
    --helm-set optimize.image.repository="gcr.io/ci-30-162810/camunda-optimize" \
    --helm-set cambpm.image.tag=${{ inputs.cambpm_version }} \
    --helm-set elasticsearch.image.tag=${{ inputs.es_version }} \
    --helm-set git.branch=${{ inputs.revision }} \
    --helm-set global.labels.app=${{ env.APP_NAME }} \
    --helm-set global.labels.commit=${{ github.sha }} \
    --name optimize-${{ env.APP_NAME }} \
    --project optimize-previews \
    --revision ${{ inputs.revision }} \
    --upsert'
}


if [[ "$1" == "persistent" ]];
then
  echo "argocd app create $(deploy_arguments) $(persistent_deploy_arguments)"
else
  echo "argocd app create $(deploy_arguments)"
fi
