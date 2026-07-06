#!/usr/bin/env bash
#
# Live demo of the WHOLE analytics stack, left running so you can open the webapp:
#
#   1. a fresh Event Bridge cluster that AUTO-CREATES topic 'zeebe-records'
#   2. an OC cluster (StandaloneCamunda) with the ZeebeRecordExporter wired in (REST :8088)
#   3. the ONE analytics application (Spring Boot, :8090): serving API + BOTH ingest stages
#      (Stage 1 projection + Stage 2 aggregation) in a single process -> serving store
#   4. a continuous driver deploying + running THREE processes (order / payment-with-gateway /
#      shipping) with real service tasks + job workers, tagged with a 'region' variable
#
# Backend is selected with ANALYTICS_BACKEND (default h2):
#   h2            embedded H2 file DB (AUTO_SERVER) — the default, no external service
#   postgres      jdbc:postgresql://… (needs a running Postgres AND the Postgres DataSource wiring)
#   elasticsearch http://…:9200      (needs a running Elasticsearch)
#
# Usage:
#   analytics/run-stack-demo.sh start [--skip-build]   # bring everything up, then exit (daemons keep running)
#   analytics/run-stack-demo.sh stop                   # tear everything down
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${EB_DEMO_DIR:-/tmp/eb-demo}"
OC_DIR="${BASE}/oc"
APP_DIR="${BASE}/app"
DRIVER_DIR="${BASE}/driver"
DB_DIR="${BASE}/db"

DIST_CP="${BASE}/dist-cp.txt"
EX_CP="${BASE}/examples-cp.txt"
APP_CP="${BASE}/app-cp.txt"

EB_CLUSTER_DIR="${EB_CLUSTER_DIR:-/tmp/eb-cluster}"
export EB_CLUSTER_DIR
export EB_TOPICS_ARGS="-Devent-bridge.topics[0].name=zeebe-records -Devent-bridge.topics[0].partition-count=1 -Devent-bridge.topics[0].replication-factor=3"

GW="http://localhost:8080"
OC_REST="http://localhost:8088"
UI="http://localhost:8090"
TOPIC="zeebe-records"
# shared file DB in AUTO_SERVER mode so the DB survives app restarts and can be inspected externally
H2_URL="jdbc:h2:file:${DB_DIR}/analytics-dataset;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"

# The analytics backend the one app uses for its serving store + the stages' per-partition stores.
BACKEND="${ANALYTICS_BACKEND:-h2}"
case "${BACKEND}" in
  h2)
    ANALYTICS_DB_ARGS=(-Danalytics.database=rdbms -DjdbcUrl="${H2_URL}" -DjdbcUser=sa) ;;
  postgres)
    ANALYTICS_DB_ARGS=(-Danalytics.database=rdbms
      -DjdbcUrl="${PG_URL:-jdbc:postgresql://localhost:5432/analytics}"
      -DjdbcUser="${PG_USER:-analytics}") ;;
  elasticsearch|opensearch)
    ANALYTICS_DB_ARGS=(-Danalytics.database="${BACKEND}"
      -Danalytics.database.url="${ES_URL:-http://localhost:9200}") ;;
  *) echo "unknown ANALYTICS_BACKEND '${BACKEND}' (h2|postgres|elasticsearch|opensearch)"; exit 2 ;;
esac

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
# The one analytics app is the webapp module, which now depends on analytics-pipeline, so its
# classpath carries both the serving layer and both ingest stages.
app_cp() { echo "${REPO_ROOT}/analytics/analytics-webapp/target/classes:$(cat "${APP_CP}")"; }

build() {
  echo "==> Building dist + examples + the analytics app (quickly; UI included)…"
  # -Dskip.fe.build=false overrides the quickly default so the React/Vite dashboard is built into
  # the app jar (target/classes/static) — otherwise -Dquickly ships the API without the UI.
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist,event-bridge/event-bridge-examples,analytics/analytics-webapp -am install -Dquickly -Dskip.fe.build=false -T1C)
  mkdir -p "${BASE}"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist dependency:build-classpath -Dmdep.outputFile="${DIST_CP}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl event-bridge/event-bridge-examples dependency:build-classpath -Dmdep.outputFile="${EX_CP}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl analytics/analytics-webapp dependency:build-classpath -Dmdep.outputFile="${APP_CP}")
  mkdir -p "${EB_CLUSTER_DIR}" && cp "${DIST_CP}" "${EB_CLUSTER_DIR}/classpath.txt"
}

stop() {
  echo "==> Tearing down the demo stack"
  for p in "${DRIVER_DIR}/pid" "${APP_DIR}/pid" "${OC_DIR}/pid"; do
    [[ -f "${p}" ]] && kill "$(cat "${p}")" 2>/dev/null || true
  done
  sleep 2
  for p in "${DRIVER_DIR}/pid" "${APP_DIR}/pid" "${OC_DIR}/pid"; do
    [[ -f "${p}" ]] && kill -9 "$(cat "${p}")" 2>/dev/null || true
    rm -f "${p}"
  done
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" stop || true
  # Belt-and-braces: kill by main class too (covers a process left over from an earlier session,
  # including a standalone AnalyticsPipeline that would otherwise double-consume the topics).
  for cls in \
    io.camunda.application.StandaloneCamunda \
    io.camunda.application.StandaloneEventBridge \
    io.camunda.analytics.webapp.AnalyticsWebappApplication \
    io.camunda.analytics.pipeline.stage.AnalyticsPipeline \
    io.camunda.eventbridge.examples.MultiProcessDemoDriver; do
    pkill -9 -f "${cls}" 2>/dev/null || true
  done
  echo "    done."
}

start() {
  [[ "${1:-}" == "--skip-build" ]] || build
  mkdir -p "${OC_DIR}" "${APP_DIR}" "${DRIVER_DIR}" "${DB_DIR}"
  rm -rf "${DB_DIR}"/analytics-dataset.* 2>/dev/null || true
  rm -rf "${DB_DIR}"/oc-rdbms.* 2>/dev/null || true
  rm -rf "${OC_DIR}/data" 2>/dev/null || true
  # wipe the app's durable stage checkpoint/rollup state (RocksDB under the app's CWD/data), else it
  # resumes from stale offsets past the end of the fresh topic and consumes nothing.
  rm -rf "${APP_DIR}/data" 2>/dev/null || true

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

  echo "==> Starting the analytics application (serving + Stage 1 + Stage 2, backend=${BACKEND}) on :8090…"
  ( cd "${APP_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(app_cp)" \
      "${ANALYTICS_DB_ARGS[@]}" \
      -Dgateway=${GW} -DinstanceId=demo -DfactsTopic=analytics-facts -DfactsPartitions=1 -DslaMs=90000 \
      io.camunda.analytics.webapp.AnalyticsWebappApplication >"${APP_DIR}/app.log" 2>&1 & echo "$!" >"${APP_DIR}/pid" )

  echo "==> Waiting for 'analytics-facts' topic (the app's Stage 1 provisions it)…"
  for _ in $(seq 1 60); do curl -fsS "${GW}/v1/topics" 2>/dev/null | grep -q analytics-facts && break || sleep 1; done

  echo "==> Waiting for the app's serving API…"
  for _ in $(seq 1 120); do
    kill -0 "$(cat "${APP_DIR}/pid")" 2>/dev/null || { echo "!! app died:"; tail -40 "${APP_DIR}/app.log"; exit 1; }
    [[ "$(curl -s -o /dev/null -w '%{http_code}' ${UI}/api/dashboard/processes)" == "200" ]] && break
    sleep 1
  done

  if [[ -n "${EB_SKIP_DRIVER:-}" ]]; then
    echo "==> EB_SKIP_DRIVER set — not starting the default multi-process driver"
  else
    echo "==> Starting the continuous multi-process driver (order / payment+gateway / shipping)…"
    ( cd "${DRIVER_DIR}" && nohup java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" \
        -Dcamunda.rest=${OC_REST} \
        io.camunda.eventbridge.examples.MultiProcessDemoDriver 1200 >"${DRIVER_DIR}/driver.log" 2>&1 & echo "$!" >"${DRIVER_DIR}/pid" )
  fi

  cat <<EOF

================================================================
  Analytics demo stack is UP (backend=${BACKEND}).

  Open the dashboard:   ${UI}

  Logs:
    OC        ${OC_DIR}/oc.log
    app       ${APP_DIR}/app.log      (serving + Stage 1 + Stage 2)
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
