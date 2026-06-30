#!/usr/bin/env bash
#
# Live demo of the WHOLE analytics stack, left running so you can open the webapp:
#
#   1. a fresh Event Bridge cluster that AUTO-CREATES topic 'zeebe-records'
#   2. an OC cluster (StandaloneCamunda) with the ZeebeRecordExporter wired in (REST :8088)
#   3. the analytics pipeline (StandaloneAnalyticsPipeline) -> shared H2 file DB (AUTO_SERVER)
#   4. the analytics webapp (Spring Boot) on :8090, reading the SAME H2 DB
#   5. a continuous driver starting timer instances tagged with a 'region' variable
#
# A dataset + report are auto-provisioned so the UI shows "execution time grouped by region".
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
export EB_TOPICS_ARGS="-Devent-bridge.topics[0].name=zeebe-records -Devent-bridge.topics[0].partition-count=3 -Devent-bridge.topics[0].replication-factor=3"

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
  for p in "${DRIVER_DIR}/pid" "${WEBAPP_DIR}/pid" "${PIPE_DIR}/pid" "${OC_DIR}/pid"; do
    [[ -f "${p}" ]] && kill "$(cat "${p}")" 2>/dev/null || true
  done
  sleep 2
  for p in "${DRIVER_DIR}/pid" "${WEBAPP_DIR}/pid" "${PIPE_DIR}/pid" "${OC_DIR}/pid"; do
    [[ -f "${p}" ]] && kill -9 "$(cat "${p}")" 2>/dev/null || true
    rm -f "${p}"
  done
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" stop || true
  echo "    done."
}

start() {
  [[ "${1:-}" == "--skip-build" ]] || build
  mkdir -p "${OC_DIR}" "${PIPE_DIR}" "${WEBAPP_DIR}" "${DRIVER_DIR}" "${DB_DIR}"
  rm -rf "${DB_DIR}"/analytics-dataset.* 2>/dev/null || true
  rm -rf "${OC_DIR}/data" 2>/dev/null || true

  echo "==> Starting a FRESH Event Bridge cluster (wiping stale per-node data)"
  rm -rf "${EB_CLUSTER_DIR}"/node-* 2>/dev/null || true
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" start --skip-build

  echo "==> Waiting for '${TOPIC}' to be ACTIVE…"
  for _ in $(seq 1 60); do curl -fsS "${GW}/v1/topics" 2>/dev/null | grep -q ACTIVE && break || sleep 1; done

  echo "==> Starting OC with the exporter (REST :8088)"
  ( cd "${OC_DIR}"
    java "${JVM_FLAGS[@]}" -cp "$(dist_cp)" \
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

  echo "==> Waiting for the OC REST API…"
  for _ in $(seq 1 180); do
    kill -0 "$(cat "${OC_DIR}/pid")" 2>/dev/null || { echo "!! OC died:"; tail -30 "${OC_DIR}/oc.log"; exit 1; }
    [[ "$(curl -s -o /dev/null -w '%{http_code}' ${OC_REST}/v2/topology)" == "200" ]] && break
    sleep 1
  done

  echo "==> Starting the analytics pipeline (shared H2 AUTO_SERVER)…"
  ( cd "${PIPE_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(analytics_cp)" \
      -Dgateway=${GW} -DinstanceId=demo -DjdbcUrl="${H2_URL}" -DjdbcUser=sa \
      io.camunda.eventbridge.analytics.StandaloneAnalyticsPipeline >"${PIPE_DIR}/pipeline.log" 2>&1 & echo "$!" >"${PIPE_DIR}/pid" )

  echo "==> Starting the analytics webapp on :8090 (same H2)…"
  ( cd "${WEBAPP_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(webapp_cp)" \
      -Danalytics.dataset.url="${H2_URL}" -Danalytics.dataset.user=sa \
      io.camunda.analytics.webapp.AnalyticsWebappApplication >"${WEBAPP_DIR}/webapp.log" 2>&1 & echo "$!" >"${WEBAPP_DIR}/pid" )

  echo "==> Waiting for the webapp API…"
  for _ in $(seq 1 120); do
    kill -0 "$(cat "${WEBAPP_DIR}/pid")" 2>/dev/null || { echo "!! webapp died:"; tail -30 "${WEBAPP_DIR}/webapp.log"; exit 1; }
    [[ "$(curl -s -o /dev/null -w '%{http_code}' ${UI}/api/datasets)" == "200" ]] && break
    sleep 1
  done

  echo "==> Auto-provisioning an hourly 'execution time by region' dataset + report"
  DS_JSON="$(curl -fsS -X POST "${UI}/api/datasets" -H 'Content-Type: application/json' \
    -d '{"name":"Execution time by region","dimensions":"region","windowSizeMs":3600000}')"
  DS_ID="$(echo "${DS_JSON}" | grep -oE '"id":[0-9]+' | head -1 | grep -oE '[0-9]+')"
  echo "    dataset id=${DS_ID}"
  curl -fsS -X POST "${UI}/api/reports" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Execution time by region\",\"datasetId\":${DS_ID},\"vizType\":\"table\"}" >/dev/null

  echo "==> Starting the continuous region driver…"
  ( cd "${DRIVER_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" \
      -Dcamunda.rest=${OC_REST} -DpauseMs=700 \
      io.camunda.eventbridge.examples.DeployAndRunRegionDemo region-exec-time-demo >"${DRIVER_DIR}/driver.log" 2>&1 & echo "$!" >"${DRIVER_DIR}/pid" )

  cat <<EOF

================================================================
  Analytics demo stack is UP.

  Open the webapp:   ${UI}
  (Report "Execution time by region" is pre-created — click "View".
   Instances take 0.5–4s by region, so give it ~30–60s to fill in,
   then hit "↻ Refresh" in the report.)

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
