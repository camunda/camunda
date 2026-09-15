#!/usr/bin/env bash
# Waits for the Optimize E2E data import to complete, without depending on a hardcoded entity count
# (the `dev-data` seed changes over time and per branch). On stable/8.8 the E2E stack runs a
# standalone Zeebe 8.5 broker that does not expose the v2 REST API, so there is no authoritative
# broker count to gate against. Instead, both the process-definition and process-instance imported
# counts in Optimize's own Elasticsearch indices are waited on until they settle non-zero.
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"

optimize_count() {
  curl -sf "${ES_URL}/$1/_count" 2>/dev/null | jq '.count // 0' || echo 0
}

# Wait until an Optimize import count settles: non-zero and stable across two consecutive reads.
# Progress is logged to stderr so the settled count is the only thing on stdout.
wait_for_settle() {
  local index="$1" attempts="$2" label="$3"
  local prev=-1 stable=0 cur=0
  for _ in $(seq 1 "$attempts"); do
    cur=$(optimize_count "$index")
    echo "${label}: ${cur}" >&2
    if [[ "$cur" -gt 0 && "$cur" == "$prev" ]]; then
      stable=$((stable + 1))
      [[ $stable -ge 2 ]] && break
    else
      stable=0
    fi
    prev="$cur"
    sleep 10
  done
  printf '%s' "$cur"
}

defs=$(wait_for_settle optimize-process-definition 30 "Optimize process definitions")
if [[ "$defs" -le 0 ]]; then
  echo "::error::Optimize imported no process definitions"; exit 1
fi
echo "Process definition import stabilized at ${defs}"

insts=$(wait_for_settle optimize-process-instance 60 "Optimize process instances")
echo "Process instance import stabilized at ${insts}"
