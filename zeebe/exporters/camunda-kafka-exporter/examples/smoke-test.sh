#!/usr/bin/env bash
set -euo pipefail

TOPIC="${1:-zeebe}"
MAX_MESSAGES="${2:-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.sm-kafka.yml"
VERIFY_SCRIPT="${SCRIPT_DIR}/verify-consumption.sh"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

echo "Starting local self-managed Kafka..."
docker compose -f "${COMPOSE_FILE}" up -d

echo "Waiting for Kafka readiness..."
for _ in $(seq 1 30); do
  if docker compose -f "${COMPOSE_FILE}" exec -T kafka kafka-topics.sh --bootstrap-server kafka:9092 --list >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "Publishing a sample record to topic '${TOPIC}'..."
printf 'smoke-0	{"schemaVersion":1,"record":{"source":"smoke-test","message":"hello"}}\n' | \
  docker compose -f "${COMPOSE_FILE}" exec -T kafka \
    kafka-console-producer.sh \
    --bootstrap-server kafka:9092 \
    --topic "${TOPIC}" \
    --property parse.key=true \
    --property key.separator=$'\t'

echo "Verifying records can be consumed again..."
bash "${VERIFY_SCRIPT}" "${TOPIC}" "${MAX_MESSAGES}"

echo "Smoke test completed successfully."
