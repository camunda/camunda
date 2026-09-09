#!/usr/bin/env bash
# Waits until Optimize has imported the one process the E2E smoke tests actually use — the
# "Order process" (definition key `orderProcess`) and at least one of its instances. Waiting for
# that specific process is deterministic; the previous global entity-count approach raced the
# still-running `dev-data` generator, which deploys its ~40 definitions in bursts, and could settle
# on a partial count during a lull (e.g. 6 of 40) and let the tests run against incomplete data.
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
PROCESS_KEY="${PROCESS_KEY:-orderProcess}"

# Optimize keeps every process instance in a per-definition index behind the
# `optimize-process-instance` read alias, so the definition key filters instances down to just the
# process we care about.
DEFINITION_QUERY='{"query":{"term":{"key":"'"${PROCESS_KEY}"'"}}}'
INSTANCE_QUERY='{"query":{"term":{"processDefinitionKey":"'"${PROCESS_KEY}"'"}}}'

es_count() {
  # $1 = index/alias, $2 = query body
  curl -sf "${ES_URL}/$1/_count" -H 'Content-Type: application/json' -d "$2" 2>/dev/null \
    | jq '.count // 0' || echo 0
}

# Wait for the process definition so it appears in the report/dashboard pickers.
for _ in $(seq 1 60); do
  cur=$(es_count optimize-process-definition "$DEFINITION_QUERY")
  echo "Optimize '${PROCESS_KEY}' definitions: ${cur}"
  [[ "$cur" -ge 1 ]] && break
  sleep 5
done
if [[ "$(es_count optimize-process-definition "$DEFINITION_QUERY")" -lt 1 ]]; then
  echo "::error::Optimize did not import the '${PROCESS_KEY}' process definition"; exit 1
fi
echo "Optimize imported the '${PROCESS_KEY}' process definition"

# Wait for at least one instance so reports built on it have data to render.
for _ in $(seq 1 60); do
  cur=$(es_count optimize-process-instance "$INSTANCE_QUERY")
  echo "Optimize '${PROCESS_KEY}' instances: ${cur}"
  [[ "$cur" -ge 1 ]] && break
  sleep 5
done
if [[ "$(es_count optimize-process-instance "$INSTANCE_QUERY")" -lt 1 ]]; then
  echo "::error::Optimize did not import any '${PROCESS_KEY}' process instances"; exit 1
fi
echo "Optimize imported '${PROCESS_KEY}' process instances"
