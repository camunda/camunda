#!/usr/bin/env bash
#
# Live demo of the WHOLE analytics stack, left running so you can open the webapp:
#
#   1. a fresh Event Bridge cluster that AUTO-CREATES topic 'zeebe-records'
#   2. an OC cluster (StandaloneCamunda) with the ZeebeRecordExporter wired in (REST :8088)
#   3. the analytics pipeline (Stage 1 projection + Stage 2 aggregation) -> shared H2 file DB (AUTO_SERVER)
#   4. the analytics webapp (Spring Boot) on :8090, reading the SAME H2 DB
#   5. a continuous driver deploying + running THREE processes (order / payment-with-gateway /
#      shipping) with real service tasks + job workers, tagged with a 'region' variable
#
# The dashboard SPA reads the serving tables directly, so no dataset/report provisioning is needed.
#
# Usage:
#   analytics/run-stack-demo.sh start [--skip-build]   # bring everything up, then exit (daemons keep running)
#   analytics/run-stack-demo.sh stop                   # tear everything down
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${EB_DEMO_DIR:-/tmp/eb-demo}"
OC_DIR="${BASE}/oc"
PIPE_DIR="${BASE}/pipeline"
WEBAPP_DIR="${BASE}/webapp"
DRIVER_DIR="${BASE}/driver"
DB_DIR="${BASE}/db"

DIST_CP="${BASE}/dist-cp.txt"
EX_CP="${BASE}/examples-cp.txt"
AN_CP="${BASE}/analytics-cp.txt"
WEB_CP="${BASE}/webapp-cp.txt"

EB_CLUSTER_DIR="${EB_CLUSTER_DIR:-/tmp/eb-cluster}"
export EB_CLUSTER_DIR
# OC (StandaloneCamunda) runs a single Zeebe partition, and the exporter routes each record to the
# event-bridge partition matching its source partition id — so ALL records land on partition 1 and
# any extra partitions stay empty (the consumer just wastes a fetch round-trip on them each sweep).
# Match the producer: one partition. Keep RF 3 for replication across the 3 nodes.
export EB_TOPICS_ARGS="-Devent-bridge.topics[0].name=zeebe-records -Devent-bridge.topics[0].partition-count=1 -Devent-bridge.topics[0].replication-factor=3"

GW="http://localhost:8080"
OC_REST="http://localhost:8088"
UI="http://localhost:8090"
TOPIC="zeebe-records"
# shared file DB in AUTO_SERVER mode: pipeline (writer) and webapp (reader) connect concurrently
H2_URL="jdbc:h2:file:${DB_DIR}/analytics-dataset;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"

JVM_FLAGS=(
  --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/java.io=ALL-UNNAMED
  --add-opens=java.base/java.util=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  -XX:ActiveProcessorCount=2
)

dist_cp() { echo "${REPO_ROOT}/dist/target/classes:$(cat "${DIST_CP}")"; }
examples_cp() { echo "${REPO_ROOT}/event-bridge/event-bridge-examples/target/classes:$(cat "${EX_CP}")"; }
analytics_cp() { echo "${REPO_ROOT}/analytics/event-bridge-analytics/target/classes:$(cat "${AN_CP}")"; }
webapp_cp() { echo "${REPO_ROOT}/analytics/analytics-webapp/target/classes:$(cat "${WEB_CP}")"; }

build() {
  echo "==> Building dist + examples + analytics + webapp (quickly)…"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist,event-bridge/event-bridge-examples,analytics/event-bridge-analytics,analytics/analytics-webapp -am install -Dquickly -T1C)
  mkdir -p "${BASE}"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist dependency:build-classpath -Dmdep.outputFile="${DIST_CP}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl event-bridge/event-bridge-examples dependency:build-classpath -Dmdep.outputFile="${EX_CP}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl analytics/event-bridge-analytics dependency:build-classpath -Dmdep.outputFile="${AN_CP}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl analytics/analytics-webapp dependency:build-classpath -Dmdep.outputFile="${WEB_CP}")
  mkdir -p "${EB_CLUSTER_DIR}" && cp "${DIST_CP}" "${EB_CLUSTER_DIR}/classpath.txt"
}

stop() {
  echo "==> Tearing down the demo stack"
  for p in "${DRIVER_DIR}/pid" "${WEBAPP_DIR}/pid" "${PIPE_DIR}/pipeline.pid" "${OC_DIR}/pid"; do
    [[ -f "${p}" ]] && kill "$(cat "${p}")" 2>/dev/null || true
  done
  sleep 2
  for p in "${DRIVER_DIR}/pid" "${WEBAPP_DIR}/pid" "${PIPE_DIR}/pipeline.pid" "${OC_DIR}/pid"; do
    [[ -f "${p}" ]] && kill -9 "$(cat "${p}")" 2>/dev/null || true
    rm -f "${p}"
  done
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" stop || true
  # Belt-and-braces: kill by main class too. The pid files only track processes THIS invocation
  # started; a pipeline/webapp left over from an earlier session (a different pid) would otherwise
  # survive — and because the analytics H2 runs in AUTO_SERVER mode, a lingering webapp keeps the
  # (even deleted) dataset alive in memory and holds :8090, so the next start can't rebind and the
  # UI keeps serving stale data. Match on the exact main classes so nothing unrelated is touched.
  for cls in \
    io.camunda.application.StandaloneCamunda \
    io.camunda.application.StandaloneEventBridge \
    io.camunda.analytics.pipeline.stage.AnalyticsPipeline \
    io.camunda.analytics.webapp.AnalyticsWebappApplication \
    io.camunda.eventbridge.examples.MultiProcessDemoDriver; do
    pkill -9 -f "${cls}" 2>/dev/null || true
  done
  echo "    done."
}

start() {
  [[ "${1:-}" == "--skip-build" ]] || build
  mkdir -p "${OC_DIR}" "${PIPE_DIR}" "${WEBAPP_DIR}" "${DRIVER_DIR}" "${DB_DIR}"
  rm -rf "${DB_DIR}"/analytics-dataset.* 2>/dev/null || true
  # OC's RDBMS secondary storage now lives in its OWN persistent H2 (AUTO_SERVER), separate from the
  # analytics app's H2, so OC can be restarted without wiping it (an in-memory H2 was lost on every
  # OC restart, wedging the RDBMS exporter). Only a FRESH start clears it.
  rm -rf "${DB_DIR}"/oc-rdbms.* 2>/dev/null || true
  rm -rf "${OC_DIR}/data" 2>/dev/null || true
  # wipe the pipeline's durable checkpoint/rollup state too — otherwise it resumes from stale
  # offsets that point past the end of the fresh topic and consumes nothing.
  rm -rf "${PIPE_DIR}/data" 2>/dev/null || true

  echo "==> Starting a FRESH Event Bridge cluster (wiping stale per-node data)"
  rm -rf "${EB_CLUSTER_DIR}"/node-* 2>/dev/null || true
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" start --skip-build

  echo "==> Waiting for '${TOPIC}' to be ACTIVE…"
  for _ in $(seq 1 60); do curl -fsS "${GW}/v1/topics" 2>/dev/null | grep -q ACTIVE && break || sleep 1; done

  echo "==> Starting OC with the exporter (REST :8088)"
  ( cd "${OC_DIR}"
    java "${JVM_FLAGS[@]}" -cp "$(dist_cp)" \
      -Dspring.profiles.active=broker,insecure,rdbmsH2 \
      -Dcamunda.data.secondary-storage.rdbms.url="jdbc:h2:file:${DB_DIR}/oc-rdbms;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1" \
      -Dlogging.level.io.camunda.db.rdbms=WARN \
      -Dserver.port=8088 -Dmanagement.server.port=9700 \
      -Dzeebe.broker.network.commandApi.port=26701 \
      -Dzeebe.broker.network.internalApi.port=26702 \
      -Dzeebe.broker.exporters.eventbridge.className=io.camunda.eventbridge.zeebe.exporter.ZeebeRecordExporter \
      -Dzeebe.broker.exporters.eventbridge.args.url=${GW} \
      -Dzeebe.broker.exporters.eventbridge.args.topic=${TOPIC} \
      -Dzeebe.broker.exporters.eventbridge.args.batchSize=5000 \
      -Dzeebe.broker.exporters.eventbridge.args.flushIntervalMs=1000 \
      -Dzeebe.broker.data.directory="${OC_DIR}/data" \
      io.camunda.application.StandaloneCamunda >"${OC_DIR}/oc.log" 2>&1 &
    echo "$!" >"${OC_DIR}/pid" )

  echo "==> Waiting for the OC REST API…"
  for _ in $(seq 1 180); do
    kill -0 "$(cat "${OC_DIR}/pid")" 2>/dev/null || { echo "!! OC died:"; tail -30 "${OC_DIR}/oc.log"; exit 1; }
    [[ "$(curl -s -o /dev/null -w '%{http_code}' ${OC_REST}/v2/topology)" == "200" ]] && break
    sleep 1
  done

  # slaMs is the SLA-met duration target (like Optimize's duration goal): 90s, matching the demo's
  # minute-scale instance durations, so ~15% of instances (the slow tier) breach it.
  echo "==> Starting the analytics pipeline (Stage 1 + Stage 2, shared actor scheduler)…"
  ( cd "${PIPE_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(analytics_cp)" \
      -Dgateway=${GW} -DinstanceId=demo -DfactsTopic=analytics-facts -DfactsPartitions=1 -DslaMs=90000 \
      -DjdbcUrl="${H2_URL}" -DjdbcUser=sa \
      io.camunda.analytics.pipeline.stage.AnalyticsPipeline \
      >"${PIPE_DIR}/pipeline.log" 2>&1 & echo "$!" >"${PIPE_DIR}/pipeline.pid" )

  echo "==> Waiting for 'analytics-facts' topic…"
  for _ in $(seq 1 60); do curl -fsS "${GW}/v1/topics" 2>/dev/null | grep -q analytics-facts && break || sleep 1; done

  echo "==> Starting the analytics webapp on :8090 (same H2)…"
  ( cd "${WEBAPP_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(webapp_cp)" \
      -Danalytics.dataset.url="${H2_URL}" -Danalytics.dataset.user=sa \
      io.camunda.analytics.webapp.AnalyticsWebappApplication >"${WEBAPP_DIR}/webapp.log" 2>&1 & echo "$!" >"${WEBAPP_DIR}/pid" )

  echo "==> Waiting for the webapp API…"
  for _ in $(seq 1 120); do
    kill -0 "$(cat "${WEBAPP_DIR}/pid")" 2>/dev/null || { echo "!! webapp died:"; tail -30 "${WEBAPP_DIR}/webapp.log"; exit 1; }
    [[ "$(curl -s -o /dev/null -w '%{http_code}' ${UI}/api/dashboard/processes)" == "200" ]] && break
    sleep 1
  done

  if [[ -n "${EB_SKIP_DRIVER:-}" ]]; then
    echo "==> EB_SKIP_DRIVER set — not starting the default multi-process driver"
    echo "    (start a load separately, e.g. analytics/run-realistic-load.sh start)"
  else
    echo "==> Starting the continuous multi-process driver (order / payment+gateway / shipping)…"
    ( cd "${DRIVER_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" \
        -Dcamunda.rest=${OC_REST} \
        io.camunda.eventbridge.examples.MultiProcessDemoDriver 1200 >"${DRIVER_DIR}/driver.log" 2>&1 & echo "$!" >"${DRIVER_DIR}/pid" )
  fi

  cat <<EOF

================================================================
  Analytics demo stack is UP.

  Open the dashboard:   ${UI}
  (Three processes — order-process, payment-process (with an exclusive
   gateway), shipping-process — run continuously. Give it ~30–60s, then
   pick a process in the header: duration percentiles, SLA-met /
   no-incident %, distinct count, top processes and the flow-node table
   all fill in from live instances.)

  Logs:
    OC        ${OC_DIR}/oc.log
    pipeline  ${PIPE_DIR}/pipeline.log
    webapp    ${WEBAPP_DIR}/webapp.log
    driver    ${DRIVER_DIR}/driver.log

  Stop everything:  analytics/run-stack-demo.sh stop
================================================================
EOF
}

case "${1:-start}" in
  start) shift || true; start "${1:-}" ;;
  stop) stop ;;
  *) echo "usage: $0 {start [--skip-build]|stop}"; exit 2 ;;
esac
