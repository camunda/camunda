#!/usr/bin/env bash
# Usage: start_broker.sh <image-variant: main|simple> <actorMetrics: true|false> [extra docker -e args...]
# Data on disk with Raft flush (fsync) disabled, to keep the laptop SSD's fsync latency out of the
# scheduler comparison.
# Single broker, 3 partitions, RF 1, cpu/io threads 3/3, 3-CPU CFS quota pinned to CPUs 8-15,
# Elasticsearch secondary storage (container bench-es on network bench-net). Like the load-test pods.
set -euo pipefail
variant=$1; actorMetrics=$2; shift 2
docker rm -f -v bench-broker >/dev/null 2>&1 || true
docker run -d --name bench-broker --network bench-net --log-driver json-file \
  --cpus=3 --cpuset-cpus="${BENCH_CPUS:-8-15}" -m 6g \
  -p 127.0.0.1:26500:26500 -p 127.0.0.1:8080:8080 -p 127.0.0.1:9600:9600 \
  -e SPRING_PROFILES_ACTIVE=broker \
  -e JAVA_OPTS="-Xms1g -Xmx1g -XX:+ExitOnOutOfMemoryError" \
  -e CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch \
  -e CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://bench-es:9200 \
  -e CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  -e CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
  -e CAMUNDA_CLUSTER_SIZE=1 -e CAMUNDA_CLUSTER_PARTITIONCOUNT=3 -e CAMUNDA_CLUSTER_REPLICATIONFACTOR=1 \
  -e CAMUNDA_SYSTEM_CPUTHREADCOUNT=3 -e CAMUNDA_SYSTEM_IOTHREADCOUNT=3 \
  -e CAMUNDA_MONITORING_METRICS_ACTOR="$actorMetrics" \
  -e CAMUNDA_CLUSTER_RAFT_FLUSHENABLED=false \
  "$@" \
  "bench/camunda:$variant" >/dev/null
timeout 300 bash -c 'until curl -sf localhost:9600/actuator/health/readiness >/dev/null 2>&1; do sleep 2; done'
echo "broker $variant ready (actor metrics: $actorMetrics)"
