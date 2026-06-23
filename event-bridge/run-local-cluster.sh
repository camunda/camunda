#!/usr/bin/env bash
#
# Starts a local 3-broker Event Bridge cluster (each node = one StandaloneEventBridge JVM:
# gateway + broker + coordinator). Each topic becomes its own Raft group, replicated across the
# three brokers; use the topology endpoint to see the assignment:
#
#   curl -s localhost:8080/v1/topology | jq
#
# Usage:
#   event-bridge/run-local-cluster.sh start        # build (quickly) + start 3 nodes
#   event-bridge/run-local-cluster.sh start --skip-build
#   event-bridge/run-local-cluster.sh stop         # kill all nodes
#   event-bridge/run-local-cluster.sh logs [0|1|2] # tail a node's log
#
# Gateways:  node-0 http://localhost:8080 | node-1 :8081 | node-2 :8082
# Data/logs: ${BASE} (default /tmp/eb-cluster)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${EB_CLUSTER_DIR:-/tmp/eb-cluster}"
CP_FILE="${BASE}/classpath.txt"
CLUSTER_SIZE=3
REPLICATION_FACTOR=3
# All three internal (SWIM) addresses, so discovery works regardless of start order.
CONTACT_POINTS="localhost:26502,localhost:26512,localhost:26522"

JVM_FLAGS=(
  --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/java.io=ALL-UNNAMED
  --add-opens=java.base/java.util=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  -XX:ActiveProcessorCount=2
)

build() {
  echo "==> Building dist (quickly)…"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist -am install -Dquickly -T1C)
  mkdir -p "${BASE}"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist dependency:build-classpath -Dmdep.outputFile="${CP_FILE}")
}

start_node() {
  local n="$1"
  local node_dir="${BASE}/node-${n}"
  mkdir -p "${node_dir}"
  local cp="${REPO_ROOT}/dist/target/classes:$(cat "${CP_FILE}")"
  echo "==> Starting node-${n} (gateway :$((8080 + n)))"
  java "${JVM_FLAGS[@]}" -cp "${cp}" \
    -Dserver.port=$((8080 + n)) \
    -Dmanagement.server.port=$((9600 + n)) \
    -Devent-bridge.cluster.node-id="broker-${n}" \
    -Devent-bridge.cluster.cluster-size="${CLUSTER_SIZE}" \
    -Devent-bridge.cluster.bind-port=$((26502 + n * 10)) \
    -Devent-bridge.cluster.command-api-port=$((26501 + n * 10)) \
    -Devent-bridge.cluster.advertised-host=localhost \
    -Devent-bridge.cluster.initial-contact-points="${CONTACT_POINTS}" \
    -Devent-bridge.raft.replication-factor="${REPLICATION_FACTOR}" \
    -Devent-bridge.data.directory="${node_dir}/data" \
    io.camunda.application.StandaloneEventBridge >"${node_dir}/node.log" 2>&1 &
  echo "$!" >"${node_dir}/pid"
}

start() {
  [[ "${1:-}" == "--skip-build" ]] || build
  for n in 0 1 2; do start_node "${n}"; done
  echo "==> Waiting for nodes to boot…"
  for n in 0 1 2; do
    for _ in $(seq 1 60); do
      if ! kill -0 "$(cat "${BASE}/node-${n}/pid")" 2>/dev/null; then
        echo "!! node-${n} died during startup:"; tail -8 "${BASE}/node-${n}/node.log"; exit 1
      fi
      grep -q "waiting for raft elections" "${BASE}/node-${n}/node.log" 2>/dev/null && break
      sleep 1
    done
  done
  echo "==> Waiting for the coordinator to serve (cluster quorum)…"
  for _ in $(seq 1 60); do
    [[ "$(curl -s -o /dev/null -w '%{http_code}' localhost:8080/v1/topics 2>/dev/null)" == "200" ]] && break
    sleep 1
  done
  echo
  echo "Cluster up. Try:"
  echo "  curl -s -XPOST localhost:8080/v1/topics -H 'Content-Type: application/json' \\"
  echo "       -d '{\"name\":\"orders\",\"partitionCount\":6,\"replicationFactor\":3}'"
  echo "  curl -s localhost:8080/v1/topology | jq"
  echo "  $0 stop"
}

stop() {
  echo "==> Stopping cluster"
  pkill -9 -f "io.camunda.application.StandaloneEventBridge" 2>/dev/null || true
}

case "${1:-start}" in
  start) shift || true; start "${1:-}" ;;
  stop) stop ;;
  logs) tail -f "${BASE}/node-${2:-0}/node.log" ;;
  *) echo "usage: $0 {start [--skip-build]|stop|logs [0|1|2]}"; exit 1 ;;
esac
