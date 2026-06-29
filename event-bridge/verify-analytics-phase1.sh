#!/usr/bin/env bash
#
# End-to-end verification of Phase 1 of the analytics pipeline against a real cluster:
#
#   1. start a fresh Event Bridge cluster that AUTO-CREATES topic 'zeebe-records'
#      (auto-create via event-bridge.topics config is robust; the POST /v1/topics admin
#      path can 503 right after startup, and stale per-node data can block metadata election)
#   2. start an OC cluster (StandaloneCamunda) with the ZeebeRecordExporter wired in
#   3. start the analytics pipeline (StandaloneAnalyticsPipeline) consuming zeebe-records
#   4. deploy + complete N auto-completing instances via the OC REST API (port 8088;
#      the embedded gRPC gateway is not relied upon)
#   5. stop the pipeline and query the H2 dataset: assert SUM(completed_count) == N
#   6. tear everything down
#
# Usage: event-bridge/verify-analytics-phase1.sh [--skip-build] [count]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${EB_VERIFY_DIR:-/tmp/eb-verify}"
OC_DIR="${BASE}/oc"
PIPE_DIR="${BASE}/pipeline"
DIST_CP_FILE="${BASE}/dist-classpath.txt"
EXAMPLES_CP_FILE="${BASE}/examples-classpath.txt"
ANALYTICS_CP_FILE="${BASE}/analytics-classpath.txt"

EB_CLUSTER_DIR="${EB_CLUSTER_DIR:-/tmp/eb-cluster}"
export EB_CLUSTER_DIR
# Auto-create zeebe-records on every node from config (idempotent on the metadata leader).
export EB_TOPICS_ARGS="-Devent-bridge.topics[0].name=zeebe-records -Devent-bridge.topics[0].partition-count=3 -Devent-bridge.topics[0].replication-factor=3"

GW="http://localhost:8080"
OC_REST="http://localhost:8088"
TOPIC="zeebe-records"
COUNT="${2:-${COUNT:-5}}"
H2_URL="jdbc:h2:file:${PIPE_DIR}/analytics-dataset;DB_CLOSE_DELAY=-1"

JVM_FLAGS=(
  --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/java.io=ALL-UNNAMED
  --add-opens=java.base/java.util=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  -XX:ActiveProcessorCount=2
)

build() {
  echo "==> Building dist + examples + analytics (quickly)…"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist,event-bridge/event-bridge-examples,event-bridge/event-bridge-analytics -am install -Dquickly -T1C)
  mkdir -p "${BASE}" "${EB_CLUSTER_DIR}"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist dependency:build-classpath -Dmdep.outputFile="${DIST_CP_FILE}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl event-bridge/event-bridge-examples dependency:build-classpath -Dmdep.outputFile="${EXAMPLES_CP_FILE}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl event-bridge/event-bridge-analytics dependency:build-classpath -Dmdep.outputFile="${ANALYTICS_CP_FILE}")
  cp "${DIST_CP_FILE}" "${EB_CLUSTER_DIR}/classpath.txt"
}

PIPE_PID=""
cleanup() {
  echo "==> Tearing down"
  [[ -n "${PIPE_PID}" ]] && kill "${PIPE_PID}" 2>/dev/null || true
  [[ -f "${OC_DIR}/pid" ]] && kill -9 "$(cat "${OC_DIR}/pid")" 2>/dev/null || true
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" stop || true
}
trap cleanup EXIT

examples_cp() { echo "${REPO_ROOT}/event-bridge/event-bridge-examples/target/classes:$(cat "${EXAMPLES_CP_FILE}")"; }
analytics_cp() { echo "${REPO_ROOT}/event-bridge/event-bridge-analytics/target/classes:$(cat "${ANALYTICS_CP_FILE}")"; }

main() {
  [[ "${1:-}" == "--skip-build" ]] || build
  mkdir -p "${OC_DIR}" "${PIPE_DIR}"
  rm -rf "${PIPE_DIR}/data" "${PIPE_DIR}/analytics-dataset.mv.db" 2>/dev/null || true

  echo "==> Starting a FRESH Event Bridge cluster (wiping stale per-node data)"
  rm -rf "${EB_CLUSTER_DIR}"/node-* 2>/dev/null || true
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" start --skip-build

  echo "==> Waiting for '${TOPIC}' to be ACTIVE (auto-created from config)"
  for _ in $(seq 1 60); do curl -fsS "${GW}/v1/topics" 2>/dev/null | grep -q ACTIVE && break || sleep 1; done
  echo "    topics: $(curl -fsS "${GW}/v1/topics" 2>/dev/null)"

  echo "==> Starting OC cluster with the exporter"
  local cp="${REPO_ROOT}/dist/target/classes:$(cat "${DIST_CP_FILE}")"
  ( cd "${OC_DIR}"
    java "${JVM_FLAGS[@]}" -cp "${cp}" \
      -Dspring.profiles.active=broker,insecure,rdbmsH2 \
      -Dserver.port=8088 -Dmanagement.server.port=9700 \
      -Dzeebe.broker.network.commandApi.port=26701 \
      -Dzeebe.broker.network.internalApi.port=26702 \
      -Dzeebe.broker.exporters.eventbridge.className=io.camunda.eventbridge.zeebe.exporter.ZeebeRecordExporter \
      -Dzeebe.broker.exporters.eventbridge.args.url=${GW} \
      -Dzeebe.broker.exporters.eventbridge.args.topic=${TOPIC} \
      -Dzeebe.broker.exporters.eventbridge.args.batchSize=1 \
      -Dzeebe.broker.exporters.eventbridge.args.flushIntervalMs=200 \
      -Dzeebe.broker.data.directory="${OC_DIR}/data" \
      io.camunda.application.StandaloneCamunda >"${OC_DIR}/oc.log" 2>&1 &
    echo "$!" >"${OC_DIR}/pid" )

  echo "==> Waiting for the OC REST API (8088 /v2/topology)…"
  for _ in $(seq 1 180); do
    kill -0 "$(cat "${OC_DIR}/pid")" 2>/dev/null || { echo "!! OC died:"; tail -30 "${OC_DIR}/oc.log"; exit 1; }
    [[ "$(curl -s -o /dev/null -w '%{http_code}' ${OC_REST}/v2/topology)" == "200" ]] && break
    sleep 1
  done

  echo "==> Starting the analytics pipeline (Phase 1)…"
  ( cd "${PIPE_DIR}" && java "${JVM_FLAGS[@]}" -cp "$(analytics_cp)" \
      -Dgateway=${GW} -DinstanceId=verify -DjdbcUrl="${H2_URL}" \
      io.camunda.eventbridge.analytics.StandaloneAnalyticsPipeline >"${PIPE_DIR}/pipeline.log" 2>&1 & echo "$!" >"${PIPE_DIR}/pid" )
  PIPE_PID="$(cat "${PIPE_DIR}/pid")"
  sleep 10  # join the consumer group + get partition assignment

  echo "==> Deploying + completing ${COUNT} instances via REST"
  java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" -Dcamunda.rest=${OC_REST} \
    io.camunda.eventbridge.examples.DeployAndCompleteProcesses exec-time-demo "${COUNT}"

  echo "==> Waiting for facts to flow + aggregate…"
  sleep 18

  echo "==> Stopping pipeline (flush + release H2)…"
  kill "${PIPE_PID}" 2>/dev/null || true
  for _ in $(seq 1 30); do kill -0 "${PIPE_PID}" 2>/dev/null || break; sleep 1; done
  PIPE_PID=""

  echo "==> Querying the windowed dataset"
  local jsh="${BASE}/query.jsh"
  cat >"${jsh}" <<EOF
import org.h2.jdbcx.JdbcDataSource; import java.sql.*;
var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:file:${PIPE_DIR}/analytics-dataset");
try (var c=ds.getConnection(); var s=c.createStatement()) {
  var rs=s.executeQuery("SELECT bpmn_process_id, window_start, completed_count FROM proc_inst_exec_time_window");
  while(rs.next()) System.out.println("ROW " + rs.getString(1) + " window=" + rs.getLong(2) + " completed=" + rs.getLong(3));
  var t=c.createStatement().executeQuery("SELECT COALESCE(SUM(completed_count),0) FROM proc_inst_exec_time_window");
  t.next(); System.out.println("TOTAL " + t.getLong(1));
}
/exit
EOF
  local out total
  out="$(jshell --class-path "$(analytics_cp)" "${jsh}" 2>/dev/null | grep -E '^ROW|^TOTAL')"
  echo "${out}"
  total="$(echo "${out}" | awk '/^TOTAL/{print $2}')"

  echo "==> completed_count total = ${total} (expected ${COUNT})"
  if [[ "${total}" == "${COUNT}" ]]; then
    echo "PHASE 1 VERIFICATION PASSED ✅"
  else
    echo "PHASE 1 VERIFICATION FAILED ❌ (got ${total:-0}, expected ${COUNT})"
    echo "--- pipeline log tail ---"; tail -40 "${PIPE_DIR}/pipeline.log" || true
    exit 1
  fi
}

main "$@"
