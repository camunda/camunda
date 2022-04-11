#!/bin/bash

die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 7 ]] || die "7 arguments required [NAMESPACE] [SQL_DUMP_NAME] [ES_VERSION] [CAMBPM_VERSION] [ES_REFRESH_INTERVAL] [EVENT_IMPORT_ENABLED] [ES_NUM_NODES], $# provided"

NAMESPACE=$1
SQL_DUMP=$2
ES_VERSION=$3
CAMBPM_VERSION=$4
ES_REFRESH_INTERVAL=$5
EVENT_IMPORT_ENABLED=$6
ES_NUM_NODES=$7

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/ns.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/secrets.yml | kubectl apply -f -

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/rbac.yml | kubectl apply -f -

# Spawning postgres
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/postgresql-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/postgresql.yml | kubectl apply -f -
# The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
# Can be removed when we migrate to kubernetes version > 1.12.0
#sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/postgresql.yml | kubectl rollout status -f - --watch=true
while ! nc -z -w 3 "postgres.$NAMESPACE" 5432; do
  sleep 15
done

# Import data in postgresql
POD_NAME=$(kubectl get po -n "$NAMESPACE" | grep postgres | cut -f1 -d' ')
gsutil -q -m cp "gs://optimize-data/${SQL_DUMP}" "/tmp/dump.sqlc"
kubectl -n "$NAMESPACE" cp "/tmp/dump.sqlc" "$POD_NAME:/db_dump/dump.sqlc"
kubectl exec -n "$NAMESPACE" "$POD_NAME" -c postgresql -it -- pg_restore --clean --if-exists -v -j 16 -h localhost -U camunda -d engine /db_dump/dump.sqlc

# Spawning elasticsearch
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/elasticsearch-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${ES_VERSION}/$ES_VERSION/g" -e "s/\${ES_NUM_NODES}/$ES_NUM_NODES/g" < .ci/podSpecs/performanceTests/elasticsearch.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${ES_VERSION}/$ES_VERSION/g" -e "s/\${ES_NUM_NODES}/$ES_NUM_NODES/g" < .ci/podSpecs/performanceTests/elasticsearch.yml | kubectl rollout status -f - --watch=true
while ! nc -z -w 3 "elasticsearch.${NAMESPACE}" 9200; do
  sleep 15
done

# Spawning cambpm
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/cambpm-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${CAMBPM_VERSION}/$CAMBPM_VERSION/g" < .ci/podSpecs/performanceTests/cambpm.yml | kubectl apply -f -

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/cambpm.yml | kubectl rollout status -f - --watch=true

# Spawning optimize
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/optimize-cfg.yml | kubectl apply -f -
kubectl -n "$NAMESPACE" create configmap performance-optimize-camunda-cloud --from-file=.ci/podSpecs/performanceTests/optimize-config/
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${ES_REFRESH_INTERVAL}/$ES_REFRESH_INTERVAL/g" -e "s/\${EVENT_IMPORT_ENABLED}/$EVENT_IMPORT_ENABLED/g" < .ci/podSpecs/performanceTests/optimize.yml | kubectl apply -f -

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/optimize.yml  | kubectl rollout status -f - --watch=true
