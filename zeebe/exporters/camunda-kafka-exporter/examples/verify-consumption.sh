#!/usr/bin/env bash
set -euo pipefail

TOPIC="${1:-zeebe}"
MAX_MESSAGES="${2:-5}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../docker-compose.sm-kafka.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

echo "Checking topic '${TOPIC}' on local self-managed Kafka..."
docker compose -f "${COMPOSE_FILE}" exec -T kafka \
  kafka-topics.sh --bootstrap-server kafka:9092 --list | grep -q "^${TOPIC}$"

echo "Consuming up to ${MAX_MESSAGES} records from topic '${TOPIC}'..."
docker compose -f "${COMPOSE_FILE}" exec -T kafka \
  kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic "${TOPIC}" \
  --from-beginning \
  --max-messages "${MAX_MESSAGES}" \
  --timeout-ms 15000 \
  --property print.headers=true \
  --property print.key=true \
  --property print.value=true
