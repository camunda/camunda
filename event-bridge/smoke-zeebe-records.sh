#!/usr/bin/env bash
#
# End-to-end smoke test for the Zeebe-records connector:
#
#   1. start a local Event Bridge cluster   (event-bridge/run-local-cluster.sh)
#   2. create the topic 'zeebe-records'
#   3. start an OC cluster (StandaloneCamunda, broker profile) with the ZeebeRecordExporter wired in
#   4. deploy + start a process -> the engine emits records -> the exporter publishes them
#   5. consume from the topic and assert Zeebe records arrived
#   6. tear everything down
#
# Usage:
#   event-bridge/smoke-zeebe-records.sh [--skip-build]
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${EB_SMOKE_DIR:-/tmp/eb-smoke}"
OC_DIR="${BASE}/oc"
DIST_CP_FILE="${BASE}/dist-classpath.txt"
EXAMPLES_CP_FILE="${BASE}/examples-classpath.txt"

EB_CLUSTER_DIR="${EB_CLUSTER_DIR:-/tmp/eb-cluster}"
export EB_CLUSTER_DIR
GW="http://localhost:8080"          # Event Bridge node-0 gateway
TOPIC="zeebe-records"
OC_HTTP_PORT=8088                   # avoid clash with the bridge gateways (8080-8082)
ZEEBE_GRPC="http://localhost:26500"

JVM_FLAGS=(
  --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
  --add-opens=java.base/java.io=ALL-UNNAMED
  --add-opens=java.base/java.util=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  -XX:ActiveProcessorCount=2
)

build() {
  echo "==> Building dist + examples (quickly)…"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist,event-bridge/event-bridge-examples -am install -Dquickly -T1C)
  mkdir -p "${BASE}" "${EB_CLUSTER_DIR}"
  (cd "${REPO_ROOT}" && ./mvnw -q -pl dist dependency:build-classpath -Dmdep.outputFile="${DIST_CP_FILE}")
  (cd "${REPO_ROOT}" && ./mvnw -q -pl event-bridge/event-bridge-examples dependency:build-classpath -Dmdep.outputFile="${EXAMPLES_CP_FILE}")
  # Let run-local-cluster.sh reuse the same dist classpath (so we can pass --skip-build).
  cp "${DIST_CP_FILE}" "${EB_CLUSTER_DIR}/classpath.txt"
}

cleanup() {
  echo "==> Tearing down"
  [[ -f "${OC_DIR}/pid" ]] && kill -9 "$(cat "${OC_DIR}/pid")" 2>/dev/null || true
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" stop || true
}
trap cleanup EXIT

examples_cp() {
  echo "${REPO_ROOT}/event-bridge/event-bridge-examples/target/classes:$(cat "${EXAMPLES_CP_FILE}")"
}

main() {
  [[ "${1:-}" == "--skip-build" ]] || build
  mkdir -p "${OC_DIR}"

  echo "==> Starting Event Bridge cluster"
  "${REPO_ROOT}/event-bridge/run-local-cluster.sh" start --skip-build

  echo "==> Creating topic '${TOPIC}'"
  curl -fsS -XPOST "${GW}/v1/topics" -H 'Content-Type: application/json' \
    -d "{\"name\":\"${TOPIC}\",\"partitionCount\":3,\"replicationFactor\":3}" >/dev/null || true
  for _ in $(seq 1 60); do
    curl -fsS "${GW}/v1/topics" | grep -q "ACTIVE" && break || sleep 1
  done
  echo "    topics: $(curl -fsS "${GW}/v1/topics")"

  echo "==> Starting OC cluster (StandaloneCamunda, broker profile) with the exporter"
  local cp="${REPO_ROOT}/dist/target/classes:$(cat "${DIST_CP_FILE}")"
  (
    cd "${OC_DIR}"
    java "${JVM_FLAGS[@]}" -cp "${cp}" \
      -Dspring.profiles.active=broker,insecure,rdbmsH2 \
      -Dserver.port=${OC_HTTP_PORT} \
      -Dmanagement.server.port=9700 \
      -Dzeebe.broker.network.commandApi.port=26701 \
      -Dzeebe.broker.network.internalApi.port=26702 \
      -Dzeebe.broker.gateway.network.port=26500 \
      -Dzeebe.broker.exporters.eventbridge.className=io.camunda.eventbridge.zeebe.exporter.ZeebeRecordExporter \
      -Dzeebe.broker.exporters.eventbridge.args.url=${GW} \
      -Dzeebe.broker.exporters.eventbridge.args.topic=${TOPIC} \
      -Dzeebe.broker.exporters.eventbridge.args.batchSize=1 \
      -Dzeebe.broker.exporters.eventbridge.args.flushIntervalMs=200 \
      -Dzeebe.broker.data.directory="${OC_DIR}/data" \
      io.camunda.application.StandaloneCamunda >"${OC_DIR}/oc.log" 2>&1 &
    echo "$!" >"${OC_DIR}/pid"
  )

  echo "==> Waiting for the Zeebe gateway (26500)…"
  for _ in $(seq 1 120); do
    if ! kill -0 "$(cat "${OC_DIR}/pid")" 2>/dev/null; then
      echo "!! OC died during startup:"; tail -30 "${OC_DIR}/oc.log"; exit 1
    fi
    (exec 3<>/dev/tcp/localhost/26500) 2>/dev/null && { exec 3>&- 3<&-; break; }
    sleep 1
  done

  echo "==> Starting consumer (before producing)…"
  java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" -Dgateway=${GW} \
    io.camunda.eventbridge.examples.ConsumeZeebeRecordsExample "${TOPIC}" "smoke-$$" \
    >"${BASE}/consumed.txt" 2>&1 &
  local consumer_pid=$!
  sleep 6   # let the consumer join the group and get its assignment

  echo "==> Deploying + starting a process"
  java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" -Dzeebe.grpc=${ZEEBE_GRPC} \
    io.camunda.eventbridge.examples.DeployAndRunProcess

  echo "==> Waiting for records to flow…"
  sleep 12

  echo "==> Fetching the topic directly (exporter-side check)…"
  java "${JVM_FLAGS[@]}" -cp "$(examples_cp)" -Dgateway=${GW} \
    io.camunda.eventbridge.examples.FetchTopic "${TOPIC}" 3 | tee "${BASE}/fetch.txt" || true

  kill "${consumer_pid}" 2>/dev/null || true
  wait "${consumer_pid}" 2>/dev/null || true
  echo "--- consumed (first 20 lines) ---"; head -20 "${BASE}/consumed.txt" || true

  echo "==> Verifying Zeebe records were consumed"
  if grep -qE "PROCESS_INSTANCE|DEPLOYMENT|JOB" "${BASE}/consumed.txt"; then
    echo "SMOKE TEST PASSED ✅"
  else
    echo "SMOKE TEST FAILED ❌ — no Zeebe records consumed"
    echo "--- topic fetch said ---"; cat "${BASE}/fetch.txt" 2>/dev/null || true
    echo "--- OC log tail ---"; tail -40 "${OC_DIR}/oc.log"
    exit 1
  fi
}

main "$@"
