#!/usr/bin/env bash
# Writes generated Zeebe records (process instances, agent definitions, agent runs,
# incidents, user tasks) into the local e2e Elasticsearch with the Optimize data
# generator, then shows in this console how Optimize imports them.
#
#   ./run-generator-e2e.sh [instances] [agent rate]
#
#   instances   process instances to generate (default 200)
#   agent rate  share of instances that run an agent, 0.0-1.0 (default 0.5)
#
# Needs: e2e-es on :9201 and Optimize running against it, with OPTIMIZE_LOG_LEVEL=DEBUG
# logging to ./optimize.log. The generator jar must be built (see the generator README).
#
# Note: an Elasticsearch whose zeebe-record indices were created by a real 8.10 exporter
# rejects the generator's documents (strict mappings, "N bulk errors" in the output); use
# a fresh Elasticsearch for generated data.
#
# Note: the generator continues after the highest record position in Elasticsearch, on
# partition 1. Records the local cluster exports afterwards have lower positions, so
# Optimize skips them; run engine tests (run-agent-e2e.sh) before this, or on a fresh
# e2e-es.
set -euo pipefail

INSTANCES="${1:-200}"
AGENT_RATE="${2:-0.5}"
ES_PORT="${ES_PORT:-9201}"
ES="http://localhost:$ES_PORT"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
JAR="$REPO/optimize/optimize-data-generator/target/optimize-data-generator-8.11.0-SNAPSHOT-shaded.jar"
JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.11-tem}"
OPTIMIZE_LOG="$HERE/optimize.log"
TIMEOUT_S="${TIMEOUT_S:-300}"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }

es() { curl -s "$ES/$1" -H 'Content-Type: application/json' ${2:+-d "$2"}; }

zeebe_runs() { # distinct agent runs in the Zeebe records
  es "zeebe-record-agent-instance/_search" '{"size":0,"aggs":{"runs":{"cardinality":{"field":"key","precision_threshold":40000}}}}' \
    | python3 -I -c 'import json,sys; print(int(json.load(sys.stdin).get("aggregations",{}).get("runs",{}).get("value",0)))' 2>/dev/null || echo 0
}

optimize_count() { # query
  es "optimize-agent-instance/_count" "$1" | python3 -I -c 'import json,sys; print(json.load(sys.stdin).get("count",0))' 2>/dev/null || echo 0
}

bold "Checking"
curl -sf -o /dev/null "$ES" || { echo "✗ Elasticsearch not reachable at $ES"; exit 1; }
echo "✓ Elasticsearch $ES"
[ -f "$JAR" ] || { echo "✗ generator jar missing: $JAR"; exit 1; }
echo "✓ Generator jar"
[ -f "$OPTIMIZE_LOG" ] && echo "✓ Optimize log $OPTIMIZE_LOG" || echo "! no Optimize log, import log lines won't show"
LOG_START=$( [ -f "$OPTIMIZE_LOG" ] && wc -l < "$OPTIMIZE_LOG" || echo 0 )

BEFORE_ZEEBE=$(zeebe_runs)
BEFORE_OPT=$(optimize_count '{"query":{"match_all":{}}}')
echo "  before: $BEFORE_ZEEBE agent runs in Zeebe records, $BEFORE_OPT in Optimize"

bold "Generating $INSTANCES process instances (agent rate $AGENT_RATE, last month)"
"$JAVA_HOME/bin/java" -jar "$JAR" --host localhost --port "$ES_PORT" \
  --instances "$INSTANCES" --agent-rate "$AGENT_RATE" --months 1 --seed "$RANDOM" 2>&1 \
  | grep -E "INFO|WARN|ERROR" | sed -E 's/^.*(INFO|WARN|ERROR) +/    generator: /' | tail -8

curl -s -o /dev/null -XPOST "$ES/zeebe-record-*/_refresh"
AFTER_ZEEBE=$(zeebe_runs)
NEW=$(( AFTER_ZEEBE - BEFORE_ZEEBE ))
echo "  generator wrote $NEW new agent runs ($AFTER_ZEEBE total in Zeebe records)"

bold "Watching the import (up to ${TIMEOUT_S}s)"
ENRICHED_QUERY='{"query":{"exists":{"field":"processInstanceState"}}}'
start=$(date +%s)
while :; do
  runs=$(optimize_count '{"query":{"match_all":{}}}')
  enriched=$(optimize_count "$ENRICHED_QUERY")
  printf '  %3ss  zeebe runs: %-6s optimize runs: %-6s enriched: %s\n' \
    "$(( $(date +%s) - start ))" "$AFTER_ZEEBE" "$runs" "$enriched"
  if [ -f "$OPTIMIZE_LOG" ]; then
    tail -n +"$((LOG_START + 1))" "$OPTIMIZE_LOG" \
      | grep -E "zeebe agent instance records into|Enriched [0-9]+ of|agent definition records|Could not enrich|Could not catch up" \
      | sed -E 's/^.*(DEBUG|INFO|WARN|ERROR) +[0-9]+ --- \[[^]]*\] [^:]*: /    optimize log: /' | tail -6 || true
    LOG_START=$(wc -l < "$OPTIMIZE_LOG")
  fi
  [ "$runs" -ge "$AFTER_ZEEBE" ] && [ "$enriched" -ge "$runs" ] && break
  [ $(( $(date +%s) - start )) -ge "$TIMEOUT_S" ] && { echo "  timed out before everything was imported"; break; }
  sleep 5
done

bold "Imported runs by agent and type"
es "optimize-agent-instance/_search" '{"size":0,"aggs":{"agents":{"terms":{"field":"agentId","size":20},"aggs":{"incidents":{"filter":{"term":{"hasIncident":true}}},"limits":{"filter":{"term":{"hitLimit":true}}},"tokens":{"avg":{"field":"totalTokens"}},"tools":{"sum":{"field":"toolCalls"}},"perTool":{"nested":{"path":"toolCallsByTool"},"aggs":{"count":{"sum":{"field":"toolCallsByTool.count"}}}}}}}}' \
  | python3 -I "$HERE/print-agent-summary.py"
echo
echo "Dashboard: http://localhost:8090 (demo/demo)"
