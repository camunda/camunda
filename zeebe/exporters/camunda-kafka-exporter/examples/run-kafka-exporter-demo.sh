#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[kafka-exporter-demo] %s\n' "$*"
}

on_error() {
  local exit_code=$?
  local line_no=${1:-unknown}
  log "Failed at line ${line_no} with exit code ${exit_code}."
  log "Tip: check stack status with: docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-camunda-kafka.yml ps"
  exit "${exit_code}"
}

trap 'on_error ${LINENO}' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.sm-camunda-kafka.yml"
BPMN_FILE="${SCRIPT_DIR}/kafka-exporter-demo.bpmn"

CAMUNDA_URL="${CAMUNDA_URL:-http://localhost:8080}"
TOPIC="${TOPIC:-zeebe}"
MAX_MESSAGES="${MAX_MESSAGES:-100}"
PROCESS_ID="kafka-exporter-demo"

log "Starting demo run"
log "Camunda URL: ${CAMUNDA_URL}"
log "Kafka topic: ${TOPIC}"
log "Process ID: ${PROCESS_ID}"

if ! command -v curl >/dev/null 2>&1; then
  log "curl is required"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  log "docker is required"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  log "jq is required"
  exit 1
fi

log "Checking Camunda API at ${CAMUNDA_URL} ..."
curl -fsS "${CAMUNDA_URL}/v3/api-docs" >/dev/null

log "Deploying BPMN: ${BPMN_FILE}"
DEPLOY_RESPONSE="$(curl -fsS -X POST "${CAMUNDA_URL}/v2/deployments" -F "resources=@${BPMN_FILE};type=text/xml")"
echo "${DEPLOY_RESPONSE}" | jq . >/dev/null

log "Starting process instance for processDefinitionId=${PROCESS_ID}"
INSTANCE_RESPONSE="$(curl -fsS -X POST "${CAMUNDA_URL}/v2/process-instances" -H 'Content-Type: application/json' -d "{\"processDefinitionId\":\"${PROCESS_ID}\"}")"
INSTANCE_KEY="$(echo "${INSTANCE_RESPONSE}" | jq -r '.processInstanceKey')"

if [[ -z "${INSTANCE_KEY}" || "${INSTANCE_KEY}" == "null" ]]; then
  log "Could not read processInstanceKey from response"
  log "Response: ${INSTANCE_RESPONSE}"
  exit 1
fi

log "Started processInstanceKey=${INSTANCE_KEY}"
log "Reading topic '${TOPIC}' for processInstanceKey='${INSTANCE_KEY}' ..."

RAW_OUTPUT="$(docker compose -f "${COMPOSE_FILE}" exec -T kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic ${TOPIC} --from-beginning --max-messages ${MAX_MESSAGES} --timeout-ms 15000 --property print.headers=true --property print.key=true --property print.value=true" 2>&1 || true)"

MATCHES="$(printf '%s\n' "${RAW_OUTPUT}" | grep -F "\"processInstanceKey\":${INSTANCE_KEY}" || true)"

if [[ -z "${MATCHES}" ]]; then
  log "No Kafka messages found for processInstanceKey='${INSTANCE_KEY}'."
  log "Tip: ensure the stack is running with:"
  log "  CAMUNDA_DOCKER_IMAGE=camunda-local:dev make kafka-exporter-camunda-stack-up"
  log "Consumer output (last lines):"
  printf '%s\n' "${RAW_OUTPUT}" | tail -n 40
  exit 1
fi

log "Success. Found exported records for '${PROCESS_ID}'."
printf '%s\n' "${MATCHES}" | tail -n 20
