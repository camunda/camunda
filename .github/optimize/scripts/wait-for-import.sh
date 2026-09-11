#!/usr/bin/env bash
# Waits for the Optimize E2E data import to complete, without depending on a hardcoded entity count
# (the `dev-data` seed changes over time and per branch). Process definitions are gated against the
# broker's authoritative count via the v2 API; instances have no clean source-of-truth count
# (completion timing, retention) so their imported count is waited on until it settles.
set -euo pipefail

ZEEBE_URL="${ZEEBE_URL:-http://localhost:8080}"
ES_URL="${ES_URL:-http://localhost:9200}"

zeebe_definition_count() {
  curl -sf -u demo:demo -H 'Content-Type: application/json' \
    -X POST "${ZEEBE_URL}/v2/process-definitions/search" \
    -d '{"page":{"limit":0}}' 2>/dev/null | jq '.page.totalItems // 0' || echo 0
}

optimize_count() {
  curl -sf "${ES_URL}/$1/_count" 2>/dev/null | jq '.count // 0' || echo 0
}

# Wait for the broker's deployment to finish: count non-zero and stable across two reads.
prev=-1; stable=0; expected=0
for _ in $(seq 1 30); do
  expected=$(zeebe_definition_count)
  echo "Zeebe process definitions: ${expected}"
  if [[ "$expected" -gt 0 && "$expected" == "$prev" ]]; then
    stable=$((stable + 1))
    [[ $stable -ge 2 ]] && break
  else
    stable=0
  fi
  prev="$expected"
  sleep 10
done
if [[ "$expected" -le 0 ]]; then
  echo "::error::Broker deployed no process definitions"; exit 1
fi
echo "Broker deployment settled at ${expected} process definitions"

# Wait until Optimize has imported all of them (>= guards against Optimize holding extras).
for _ in $(seq 1 30); do
  cur=$(optimize_count optimize-process-definition)
  echo "Optimize process definitions: ${cur}/${expected}"
  [[ "$cur" -ge "$expected" ]] && break
  sleep 10
done
cur=$(optimize_count optimize-process-definition)
if [[ "$cur" -lt "$expected" ]]; then
  echo "::error::Optimize imported only ${cur}/${expected} process definitions"; exit 1
fi
echo "Optimize imported all ${expected} process definitions"

# Instances have no clean source-of-truth count, so wait for the imported count to settle non-zero.
prev=-1; stable=0; cur=0
for _ in $(seq 1 60); do
  cur=$(optimize_count optimize-process-instance)
  echo "Optimize process instances: ${cur}"
  if [[ "$cur" -gt 0 && "$cur" == "$prev" ]]; then
    stable=$((stable + 1))
    [[ $stable -ge 2 ]] && break
  else
    stable=0
  fi
  prev="$cur"
  sleep 10
done
echo "Process instance import stabilized at ${cur}"
