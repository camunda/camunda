#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 4 ]] || die "4 arguments required [NAMESPACE, DOCKER_REGISTRY_USER, DOCKER_REGISTRY_PW, SQL_DUMP_NAME], $# provided"

NAMESPACE=$1
REGISTRY_USR=$2
REGISTRY_PSW=$3
SQL_DUMP=$4

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/ns.yml | kubectl apply -f -
kubectl create secret docker-registry registry-camunda-cloud-secret \
    --namespace "$NAMESPACE" \
    --docker-server=https://registry.camunda.cloud \
    --docker-username="$REGISTRY_USR" \
    --docker-password="$REGISTRY_PSW" \
    --docker-email=ci@camunda.com

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/rbac.yml | kubectl apply -f -

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
kubectl exec -n "$NAMESPACE" "$POD_NAME" -c gcloud -it -- gsutil -q -m cp "gs://camunda-ops/optimize/$SQL_DUMP" /db_dump/dump.sqlc
kubectl exec -n "$NAMESPACE" "$POD_NAME" -c postgresql -it -- pg_restore --clean --if-exists -v -j 16 -h localhost -U camunda -d engine /db_dump/dump.sqlc

#Spawning elasticsearch
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/elasticsearch-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/elasticsearch.yml | kubectl apply -f -
# The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
# Can be removed when we migrate to kubernetes version > 1.12.0
#sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/elasticsearch.yml | kubectl rollout status -f - --watch=true
while ! nc -z -w 3 elasticsearch.${NAMESPACE} 9200; do
  sleep 15
done

#Spawning cambpm
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/cambpm-cfg.yml | kubectl apply -f -
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/cambpm.yml | kubectl apply -f -

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/cambpm.yml | kubectl rollout status -f - --watch=true

#Spawning optimize
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/optimize-cfg.yml | kubectl apply -f -
kubectl -n "$NAMESPACE" create configmap performance-optimize-camunda-cloud --from-file=.ci/podSpecs/performanceTests/optimize-config/
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/optimize.yml | kubectl apply -f -

sed -e "s/\${NAMESPACE}/$NAMESPACE/g" < .ci/podSpecs/performanceTests/optimize.yml  | kubectl rollout status -f - --watch=true