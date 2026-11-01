#!/usr/bin/env bash
# Starts agent process instances on the local 8.10 cluster and shows, in this
# console, what the engine exports to Zeebe records and what Optimize imports
# from them.
#
#   ./run-agent-e2e.sh [count] [ticket text] [process id]
#
#   count       instances to start (default 1)
#   ticket      the customer message the agent answers
#   process id  order-support-agent-mock (default, mock LLM, no key needed)
#               or order-support-agent (real Anthropic, needs start-connectors.sh with a key)
#
# Needs: e2e-camunda, e2e-es and e2e-connectors running, Optimize started with
# OPTIMIZE_LOG_LEVEL=DEBUG logging to ./optimize.log, and for the mock process
# mock-anthropic.py running on port 8099.
set -euo pipefail

COUNT="${1:-1}"
TICKET="${2:-My order A-1001 arrived damaged, can I get a refund?}"
PROCESS_ID="${3:-order-support-agent-mock}"
CLUSTER=http://localhost:8088
ES=http://localhost:9201
HERE="$(cd "$(dirname "$0")" && pwd)"
OPTIMIZE_LOG="$HERE/optimize.log"
TIMEOUT_S=180

bold() { printf '\033[1m%s\033[0m\n' "$*"; }

check() {
  curl -sf -o /dev/null "$2" || { echo "✗ $1 not reachable at $2"; exit 1; }
  echo "✓ $1"
}

bold "Checking the stack"
check "Camunda cluster" "$CLUSTER/v2/topology"
check "Elasticsearch" "$ES"
docker ps --format '{{.Names}}' | grep -qx e2e-connectors || { echo "✗ connectors not running: ./start-connectors.sh --mock"; exit 1; }
echo "✓ Connectors"
if [ "$PROCESS_ID" = "order-support-agent-mock" ]; then
  curl -s -o /dev/null -X POST localhost:8099/v1/messages -d '{}' || { echo "✗ mock LLM not running: python3 -I mock-anthropic.py"; exit 1; }
  echo "✓ Mock LLM"
fi
[ -f "$OPTIMIZE_LOG" ] && echo "✓ Optimize log at $OPTIMIZE_LOG" || echo "! no Optimize log at $OPTIMIZE_LOG, import log lines won't show"
LOG_START=$( [ -f "$OPTIMIZE_LOG" ] && wc -l < "$OPTIMIZE_LOG" || echo 0 )

bold "Starting $COUNT instance(s) of $PROCESS_ID"
KEYS=()
for _ in $(seq 1 "$COUNT"); do
  body=$(python3 -I -c 'import json,sys; print(json.dumps({"processDefinitionId": sys.argv[1], "variables": {"ticket": sys.argv[2]}}))' "$PROCESS_ID" "$TICKET")
  key=$(curl -sf -u demo:demo -X POST "$CLUSTER/v2/process-instances" -H 'Content-Type: application/json' -d "$body" \
    | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["processInstanceKey"])')
  KEYS+=("$key")
  echo "  process instance $key"
done
KEYS_JSON=$(printf '%s\n' "${KEYS[@]}" | python3 -I -c 'import json,sys; print(json.dumps([l.strip() for l in sys.stdin if l.strip()]))')

es_count() { # index pattern, query json
  curl -s "$ES/$1/_count" -H 'Content-Type: application/json' -d "$2" \
    | python3 -I -c 'import json,sys; print(json.load(sys.stdin).get("count", 0))' 2>/dev/null || echo 0
}

RECORD_QUERY=$(python3 -I -c 'import json,sys; print(json.dumps({"query": {"terms": {"value.processInstanceKey": json.loads(sys.argv[1])}}}))' "$KEYS_JSON")
RUN_QUERY=$(python3 -I -c 'import json,sys; print(json.dumps({"query": {"terms": {"processInstanceId": json.loads(sys.argv[1])}}}))' "$KEYS_JSON")
DONE_QUERY=$(python3 -I -c 'import json,sys; print(json.dumps({"query": {"bool": {"filter": [{"terms": {"processInstanceId": json.loads(sys.argv[1])}}, {"exists": {"field": "endDate"}}, {"exists": {"field": "processInstanceState"}}]}}}))' "$KEYS_JSON")

bold "Watching (up to ${TIMEOUT_S}s). Columns: engine agent records | Optimize runs | completed + enriched runs"
start=$(date +%s)
while :; do
  records=$(es_count "zeebe-record_agent-instance_*" "$RECORD_QUERY")
  runs=$(es_count "optimize-agent-instance" "$RUN_QUERY")
  done_runs=$(es_count "optimize-agent-instance" "$DONE_QUERY")
  printf '  %3ss  zeebe records: %-4s  optimize runs: %-3s  completed+enriched: %s/%s\n' \
    "$(( $(date +%s) - start ))" "$records" "$runs" "$done_runs" "$COUNT"
  if [ -f "$OPTIMIZE_LOG" ]; then
    tail -n +"$((LOG_START + 1))" "$OPTIMIZE_LOG" \
      | grep -E "zeebe agent instance records|Enriched [0-9]+ of|agent definition|Could not enrich|Could not catch up" \
      | sed -E 's/^.*(DEBUG|INFO|WARN|ERROR) +[0-9]+ --- \[[^]]*\] [^:]*: /    optimize log: /' || true
    LOG_START=$(wc -l < "$OPTIMIZE_LOG")
  fi
  [ "$done_runs" -ge "$COUNT" ] && break
  [ $(( $(date +%s) - start )) -ge "$TIMEOUT_S" ] && { echo "  timed out; check docker logs e2e-connectors and Operate"; break; }
  sleep 5
done

bold "Agent runs in Optimize (optimize-agent-instance)"
curl -s "$ES/optimize-agent-instance/_search" -H 'Content-Type: application/json' -d "$RUN_QUERY" | python3 -I "$HERE/print-runs.py"
echo
echo "Dashboard: http://localhost:8090 (demo/demo)   Operate: $CLUSTER/operate (demo/demo)"
