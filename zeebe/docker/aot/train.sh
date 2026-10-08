#!/bin/sh
# Trains an AOT cache by booting Camunda and driving a workload through it, so that the
# cache carries the classes and method profiles of command processing and of both the RDBMS
# and Elasticsearch exporters, not only start-up.
#
# The broker that records the cache runs in a cluster of three, with two more brokers on
# this host, so that the cache also carries replication and messaging between brokers and a
# follower taking over a partition's leadership, which a broker on its own never does.
#
# Usage: train.sh <cache output> <directory holding the compiled AotTrainingWorkload>
set -eu

cache="$1"
workload="$2"
partitions=3
instances=5000
leader_changes=6
log="${CAMUNDA_HOME}/logs/aot-training.log"
peers="$(mktemp -d)"

# One training run can only record one profile, so it exercises both exporters at once: the
# RDBMS exporter, which only exists with RDBMS storage, against an in-memory H2, and the
# Camunda exporter against the workload's fake Elasticsearch. Authentication is off because
# checking credentials needs a populated secondary storage or an identity provider, except for
# the cluster-admin API, whose users are configured here, as the workload asks it to rebalance.
#
# Usage: broker <id> <data directory> <JAVA_OPTS>. Broker <id> listens on the default ports
# plus 10 * <id> for gRPC and the cluster, and plus <id> for REST and management. It replaces
# the shell it runs in, so that the PID of a broker started in the background is the JVM's,
# which the workload pauses and resumes.
broker() {
  id="$1"
  JAVA_OPTS="$3" \
  CAMUNDA_DATA_PRIMARYSTORAGE_DIRECTORY="$2" \
  CAMUNDA_CLUSTER_NODEID="${id}" \
  CAMUNDA_CLUSTER_SIZE=3 \
  CAMUNDA_CLUSTER_REPLICATIONFACTOR=3 \
  CAMUNDA_CLUSTER_PARTITIONCOUNT="${partitions}" \
  CAMUNDA_CLUSTER_INITIALCONTACTPOINTS=localhost:26502,localhost:26512,localhost:26522 \
  CAMUNDA_CLUSTER_NETWORK_ADVERTISEDHOST=localhost \
  CAMUNDA_CLUSTER_NETWORK_COMMANDAPI_PORT=$((26501 + 10 * id)) \
  CAMUNDA_CLUSTER_NETWORK_INTERNALAPI_PORT=$((26502 + 10 * id)) \
  CAMUNDA_API_GRPC_PORT=$((26500 + 10 * id)) \
  SERVER_PORT=$((8080 + id)) \
  MANAGEMENT_SERVER_PORT=$((9600 + id)) \
  CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=rdbms \
  CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_URL=jdbc:h2:mem:camunda \
  CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_USERNAME=sa \
  SPRING_APPLICATION_JSON='{"camunda":{"data":{"exporters":{"camundaes":{
    "class-name":"io.camunda.exporter.CamundaExporter",
    "args":{"connect":{"type":"elasticsearch","url":"http://localhost:9200"},"createSchema":false}}}}}}' \
  CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
  CAMUNDA_SECURITY_CLUSTERADMIN_BASIC_USERS_0_NAME=aot-training \
  CAMUNDA_SECURITY_CLUSTERADMIN_BASIC_USERS_0_PASSWORD=aot-training \
    exec "${CAMUNDA_HOME}/bin/camunda"
}

for id in 1 2; do
  mkdir "${peers}/${id}"
  broker "${id}" "${peers}/${id}/data" -Xmx1g > "${peers}/${id}/broker.log" 2>&1 &
  echo $! > "${peers}/${id}/pid"
done
broker 0 "${CAMUNDA_HOME}/data" "-XX:AOTCacheOutput=${cache}" > "${log}" 2>&1 &
camunda=$!
# A failed workload must fail the build with the broker's log in it, rather than leave the
# build waiting on brokers that never exit.
trap 'kill -CONT "${camunda}" 2>/dev/null || true
  kill -TERM "${camunda}" $(cat "${peers}"/*/pid) 2>/dev/null || true
  tail -n 200 "${log}"; rm -rf "${peers}"' EXIT

java -cp "${workload}:${CAMUNDA_HOME}/lib/*" AotTrainingWorkload \
  "${instances}" "${partitions}" "${leader_changes}" "${camunda}"

# The JVM writes the cache as it exits, and a SIGTERM exit reports 143.
kill -TERM "${camunda}"
wait "${camunda}" || [ $? -eq 143 ]
for id in 1 2; do
  kill -TERM "$(cat "${peers}/${id}/pid")"
  wait "$(cat "${peers}/${id}/pid")" || true
done
trap - EXIT
rm -rf "${peers}"
test -s "${cache}"
