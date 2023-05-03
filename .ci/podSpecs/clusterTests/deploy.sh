#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 3 ]] || die "3 arguments required [NAMESPACE] [ES_VERSION] [CAMPBM_VERSION], $# provided"

NAMESPACE=$1
ES_VERSION=$2
CAMBPM_VERSION=$3

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/ns.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/secrets.yml | kubectl apply -f -

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/rbac.yml | kubectl apply -f -

#Spawning elasticsearch
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/elasticsearch-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${ES_VERSION}/$ES_VERSION/g" < .ci/podSpecs/clusterTests/elasticsearch.yml | kubectl apply -f -
# The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
# Can be removed when we migrate to kubernetes version > 1.12.0
#kubectl rollout status -f .ci/podSpecs/clusterTests/elasticsearch.yml --watch=true
while ! nc -z -w 3 "elasticsearch.$NAMESPACE" 9200; do
  sleep 15
done

#Spawning cambpm
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/cambpm-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${CAMBPM_VERSION}/$CAMBPM_VERSION/g" < .ci/podSpecs/clusterTests/cambpm.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/cambpm.yml | kubectl rollout status -f - --watch=true

#Spawning optimize-no-import-cluster (No import cluster with >1 replicas)
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/optimize-no-import-cluster-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/optimize-no-import-cluster.yml | kubectl apply -f -
# The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
# Can be removed when we migrate to kubernetes version > 1.12.0
#kubectl rollout status -f .ci/podSpecs/clusterTests/elasticsearch.yml --watch=true
while ! nc -z -w 3 "optimize-no-import.$NAMESPACE" 8090; do
  sleep 15
done

#Spawning optimize-import (Will import data from engine, only one instance)
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/optimize-import-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/optimize-import.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/clusterTests/optimize-import.yml | kubectl rollout status -f - --watch=true

#Monitoring Import of optimize-import (Should be true till data got imported)
IMPORTING="true"
until [[ ${IMPORTING} = "false" ]]; do
    # note: each call here is followed by `|| true` to not let the whole script fail if the curl call fails due to potential downtimes of pods
    curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true
    IMPORTING=$(curl "http://optimize-import.${NAMESPACE}:8090/api/status" | jq '.engineStatus."camunda-bpm".isImporting') || true
    sleep 10
done
