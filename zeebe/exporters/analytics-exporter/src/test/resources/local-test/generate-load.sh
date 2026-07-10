#!/usr/bin/env bash
# Deploys 2 processes and creates instances in a loop.
# Usage: ./generate-load.sh [instances-per-second] [duration-seconds]
# Defaults: 5 instances/sec for 120 seconds

set -euo pipefail

API="http://localhost:8080/v2"
RATE="${1:-5}"
DURATION="${2:-120}"

if [ "$RATE" -le 0 ] 2>/dev/null; then
  echo "Error: rate must be a positive integer" >&2
  exit 1
fi

SLEEP=$(awk "BEGIN {printf \"%.3f\", 1/$RATE}")

echo "Waiting for Zeebe to be ready..."
until curl -sf "$API/../v1/topology" > /dev/null 2>&1; do sleep 1; done

echo "Deploying processes..."

for PROCESS_ID in order-process payment-flow; do
  curl -sf --retry 3 --retry-connrefused -X POST "$API/deployments" \
    -F "resources=@-;filename=$PROCESS_ID.bpmn;type=application/xml" <<BPMN
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://camunda.org/test">
  <bpmn:process id="$PROCESS_ID" isExecutable="true">
    <bpmn:startEvent id="start"/><bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>
BPMN
  echo "  Deployed $PROCESS_ID"
done

PROCESSES=("order-process" "payment-flow")
COUNT=0
START=$(date +%s)

echo ""
echo "Creating ~$RATE instances/sec for ${DURATION}s..."
echo "Grafana: http://localhost:3333  Prometheus: http://localhost:9090"
echo ""

while true; do
  ELAPSED=$(( $(date +%s) - START ))
  if [ "$ELAPSED" -ge "$DURATION" ]; then break; fi

  PROCESS="${PROCESSES[$((COUNT % ${#PROCESSES[@]}))]}"
  curl -sf -X POST "$API/process-instances" \
    -H "Content-Type: application/json" \
    -d "{\"bpmnProcessId\":\"$PROCESS\"}" > /dev/null

  COUNT=$((COUNT + 1))
  if [ $((COUNT % 50)) -eq 0 ]; then
    echo "  $COUNT instances (${ELAPSED}s)"
  fi

  sleep "$SLEEP"
done

echo ""
echo "Done — created $COUNT instances in ${DURATION}s"
echo "Wait ~10s for the next metric flush, then check Grafana."
