#!/usr/bin/env bash
# run-kafka-exporter-demo-mtls.sh
#
# End-to-end demo for the mutual TLS (mTLS) stack.
# Starts the mTLS-secured Camunda + Kafka stack, deploys a BPMN process, starts a process
# instance, and verifies that the exported records appear on the Kafka topic.  The consumer
# authenticates to the broker by presenting the Zeebe client certificate (CN=zeebe).
#
# Prerequisites:
#   bash zeebe/exporters/camunda-kafka-exporter/examples/gen-certs.sh
#
# Usage:
#   bash zeebe/exporters/camunda-kafka-exporter/examples/run-kafka-exporter-demo-mtls.sh
#
# Environment overrides:
#   CAMUNDA_DOCKER_IMAGE   default: camunda/camunda:SNAPSHOT
#   CAMUNDA_URL            default: http://localhost:8080
#   TOPIC                  default: zeebe
#   MAX_MESSAGES           default: 100
set -euo pipefail

LABEL="kafka-exporter-demo-mtls"

log() {
  printf '[%s] %s\n' "${LABEL}" "$*"
}

on_error() {
  local exit_code=$?
  local line_no=${1:-unknown}
  log "Failed at line ${line_no} with exit code ${exit_code}."
  log "Tip: check stack status with: docker compose -f ${COMPOSE_FILE} ps"
  exit "${exit_code}"
}

trap 'on_error ${LINENO}' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.sm-kafka-mtls.yml"
BPMN_FILE="${SCRIPT_DIR}/kafka-exporter-demo.bpmn"
CERTS_DIR="${SCRIPT_DIR}/certs"

CAMUNDA_URL="${CAMUNDA_URL:-http://localhost:8080}"
TOPIC="${TOPIC:-zeebe}"
MAX_MESSAGES="${MAX_MESSAGES:-100}"
PROCESS_ID="kafka-exporter-demo"

# Internal bootstrap address (SSL listener inside the Docker network).
INTERNAL_BOOTSTRAP="kafka:19094"

# Path to Kafka console scripts in the apache/kafka image.
KAFKA_BIN="/opt/kafka/bin"

for cmd in curl docker jq; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    log "${cmd} is required"
    exit 1
  fi
done

for cert in truststore.p12 client-zeebe.p12; do
  if [[ ! -f "${CERTS_DIR}/${cert}" ]]; then
    log "Certificate ${cert} not found. Run: bash ${SCRIPT_DIR}/gen-certs.sh"
    exit 1
  fi
done

# Verify the image contains the Kafka exporter class. If missing, the broker will fail at
# startup with ClassNotFoundException. Build a local image first:
#   docker build -f camunda.Dockerfile --build-arg BASE=public -t camunda/camunda:SNAPSHOT .
DOCKER_IMAGE="${CAMUNDA_DOCKER_IMAGE:-camunda/camunda:SNAPSHOT}"
log "Verifying Kafka exporter is bundled in image: ${DOCKER_IMAGE} ..."
if ! docker run --rm --entrypoint sh "${DOCKER_IMAGE}" \
    -c 'find /usr/local/camunda/lib -name "camunda-kafka-exporter*.jar" | grep -q .' 2>/dev/null; then
  log "ERROR: camunda-kafka-exporter JAR not found in image '${DOCKER_IMAGE}'."
  log "Build a local image with the exporter bundled first:"
  log "  docker build -f camunda.Dockerfile --build-arg BASE=public -t camunda/camunda:SNAPSHOT ."
  exit 1
fi
log "Kafka exporter JAR found in image."

log "Starting demo: mutual TLS (mTLS)"
log "Compose file : ${COMPOSE_FILE}"
log "Camunda URL  : ${CAMUNDA_URL}"
log "Kafka topic  : ${TOPIC}"

log "Starting stack (docker compose up -d) ..."
docker compose -f "${COMPOSE_FILE}" up -d

log "Waiting for Camunda to be ready..."
for i in $(seq 1 60); do
  if curl -fsS "${CAMUNDA_URL}/v3/api-docs" >/dev/null 2>&1; then
    break
  fi
  if [[ "${i}" -eq 60 ]]; then
    log "Camunda did not become ready in time."
    exit 1
  fi
  sleep 3
done

log "Deploying BPMN: ${BPMN_FILE}"
DEPLOY_RESPONSE="$(curl -fsS -X POST "${CAMUNDA_URL}/v2/deployments" -F "resources=@${BPMN_FILE};type=text/xml")"
echo "${DEPLOY_RESPONSE}" | jq . >/dev/null

log "Starting process instance for processDefinitionId=${PROCESS_ID}"
INSTANCE_RESPONSE="$(curl -fsS -X POST "${CAMUNDA_URL}/v2/process-instances" \
  -H 'Content-Type: application/json' \
  -d "{\"processDefinitionId\":\"${PROCESS_ID}\"}")"
INSTANCE_KEY="$(echo "${INSTANCE_RESPONSE}" | jq -r '.processInstanceKey')"

if [[ -z "${INSTANCE_KEY}" || "${INSTANCE_KEY}" == "null" ]]; then
  log "Could not read processInstanceKey from response"
  log "Response: ${INSTANCE_RESPONSE}"
  exit 1
fi

log "Started processInstanceKey=${INSTANCE_KEY}"
log "Reading topic '${TOPIC}' via mTLS (client cert CN=zeebe) ..."

# Both the truststore and the Zeebe client keystore are available in the kafka container at
# /certs/ via the ./certs:/certs:ro bind mount (see docker-compose.sm-kafka-mtls.yml).
CONSUMER_PROPS="$(cat << 'EOF'
security.protocol=SSL
ssl.truststore.location=/certs/truststore.p12
ssl.truststore.password=changeit
ssl.truststore.type=PKCS12
ssl.keystore.location=/certs/client-zeebe.p12
ssl.keystore.password=changeit
ssl.keystore.type=PKCS12
EOF
)"

RAW_OUTPUT="$(docker compose -f "${COMPOSE_FILE}" exec -T kafka bash -lc "
  printf '%s' '${CONSUMER_PROPS}' > /tmp/consumer.properties
  ${KAFKA_BIN}/kafka-console-consumer.sh \\
    --bootstrap-server ${INTERNAL_BOOTSTRAP} \
    --consumer.config /tmp/consumer.properties \
    --topic ${TOPIC} \
    --from-beginning \
    --max-messages ${MAX_MESSAGES} \
    --timeout-ms 15000 \
    --property print.headers=true \
    --property print.key=true \
    --property print.value=true
" 2>&1 || true)"

MATCHES="$(printf '%s\n' "${RAW_OUTPUT}" | grep -F "\"processInstanceKey\":${INSTANCE_KEY}" || true)"

if [[ -z "${MATCHES}" ]]; then
  log "No Kafka messages found for processInstanceKey='${INSTANCE_KEY}'."
  log "Consumer output (last lines):"
  printf '%s\n' "${RAW_OUTPUT}" | tail -n 40
  exit 1
fi

log "Success. Found exported records for processInstanceKey=${INSTANCE_KEY} via mTLS."
printf '%s\n' "${MATCHES}" | tail -n 20
