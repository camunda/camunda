#!/bin/sh
# Trains an AOT cache by booting Camunda and driving a workload through it, so that the
# cache carries the classes and method profiles of command processing and of both the RDBMS
# and Elasticsearch exporters, not only start-up.
#
# Usage: train.sh <cache output> <directory holding the compiled AotTrainingWorkload>
set -eu

cache="$1"
workload="$2"
partitions=3
instances=5000
log="${CAMUNDA_HOME}/logs/aot-training.log"

# One training run can only record one profile, so it exercises both exporters at once: the
# RDBMS exporter, which only exists with RDBMS storage, against an in-memory H2, and the
# Camunda exporter against the workload's fake Elasticsearch. Authentication is off because
# checking credentials needs a populated secondary storage or an identity provider.
CAMUNDA_CLUSTER_PARTITIONCOUNT="${partitions}" \
CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=rdbms \
CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_URL=jdbc:h2:mem:camunda \
CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_USERNAME=sa \
SPRING_APPLICATION_JSON='{"camunda":{"data":{"exporters":{"camundaes":{
  "class-name":"io.camunda.exporter.CamundaExporter",
  "args":{"connect":{"type":"elasticsearch","url":"http://localhost:9200"},"createSchema":false}}}}}}' \
CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
JAVA_OPTS="-XX:AOTCacheOutput=${cache}" \
  "${CAMUNDA_HOME}/bin/camunda" > "${log}" 2>&1 &
camunda=$!
# A failed workload must fail the build with the broker's log in it, rather than leave the
# build waiting on a broker that never exits.
trap 'kill -TERM "${camunda}" 2>/dev/null || true; tail -n 200 "${log}"' EXIT

java -cp "${workload}:${CAMUNDA_HOME}/lib/*" AotTrainingWorkload "${instances}" "${partitions}"

# The JVM writes the cache as it exits, and a SIGTERM exit reports 143.
kill -TERM "${camunda}"
wait "${camunda}" || [ $? -eq 143 ]
trap - EXIT
test -s "${cache}"
