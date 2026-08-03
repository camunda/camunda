#!/usr/bin/env bash
# Querying Prometheus, and asserting on what it answers.
#
# Every scenario's verdict comes from here rather than from the endpoint alone:
# a run whose panels stay empty has proved nothing, and the only way to know
# that during the run rather than after it is to assert on the metrics as it
# goes. Sourced by run.sh; not runnable on its own.

# --- Queries ---------------------------------------------------------------

# prom_query <promql> — the first sample's value, or "" when the query matched
# no series. $NS is substituted for $NAMESPACE, so callers write queries the way
# the dashboard does.
prom_query() {
  local promql=${1//\$NAMESPACE/$NS}
  local response
  response=$(curl -sS -m 20 -G "$PROM_URL/api/v1/query" --data-urlencode "query=$promql" 2>/dev/null) || {
    warn "Prometheus query failed: $promql"
    return 1
  }
  if [[ "$(jq -r '.status' <<<"$response")" != "success" ]]; then
    warn "Prometheus rejected: $promql — $(jq -r '.error // "unknown error"' <<<"$response")"
    return 1
  fi
  jq -r '.data.result[0].value[1] // ""' <<<"$response"
}

# prom_series <promql> — every series the query matched, one `labels value` line
# each, for the reads that are about which partitions rather than how many.
prom_series() {
  local promql=${1//\$NAMESPACE/$NS}
  curl -sS -m 20 -G "$PROM_URL/api/v1/query" --data-urlencode "query=$promql" 2>/dev/null \
    | jq -r '.data.result[]? | "\(.metric | del(.__name__) | tostring) \(.value[1])"'
}

# prom_snapshot <name> <promql> — keeps a query's full answer next to the
# scenario, for the reads worth having in full afterwards.
prom_snapshot() {
  local name=$1
  local promql=${2//\$NAMESPACE/$NS}
  curl -sS -m 20 -G "$PROM_URL/api/v1/query" --data-urlencode "query=$promql" 2>/dev/null \
    | jq '.' > "$SCENARIO_DIR/prom-$name.json"
}

# --- Assertions ------------------------------------------------------------

# assert_num <label> <promql> <op> <expected> [timeout_s]
#
# Retried until the timeout, because a scrape interval passes before a metric
# can possibly answer for something that just happened, and a gossiped gauge
# takes a few more to converge. Reports the actual value either way, so a
# failure says what was seen rather than only that it was wrong.
assert_num() {
  local label=$1 promql=$2 op=$3 expected=$4 timeout=${5:-$ASSERT_TIMEOUT}
  local started actual verdict
  started=$(date +%s)

  while :; do
    actual=$(prom_query "$promql" || echo "")
    if [[ -n "$actual" ]] && compare_num "$actual" "$op" "$expected"; then
      verdict=PASS
      break
    fi
    if (( $(date +%s) - started >= timeout )); then
      verdict=FAIL
      break
    fi
    sleep 5
  done

  [[ -z "$actual" ]] && actual="<no series>"
  record_assertion "$verdict" "$label" "$op $expected" "$actual" "$promql"
  [[ "$verdict" == PASS ]]
}

# assert_absent <label> <promql> [timeout_s]
#
# For the gauges the coordinator tears down when a rebalance ends: a series
# left standing would draw a finished rebalance as still running.
assert_absent() {
  local label=$1 promql=$2 timeout=${3:-$ASSERT_TIMEOUT}
  local started actual verdict
  started=$(date +%s)

  while :; do
    actual=$(prom_query "$promql" || echo "")
    if [[ -z "$actual" ]]; then
      verdict=PASS
      break
    fi
    if (( $(date +%s) - started >= timeout )); then
      verdict=FAIL
      break
    fi
    sleep 5
  done

  record_assertion "$verdict" "$label" "no series" "${actual:-<no series>}" "$promql"
  [[ "$verdict" == PASS ]]
}

# assert_delta <label> <promql> <op> <expected> <baseline> [timeout_s]
#
# Counters are cumulative, so what a scenario did is the change across it
# rather than the total. The baseline comes from prom_baseline before the
# scenario acted.
assert_delta() {
  local label=$1 promql=$2 op=$3 expected=$4 baseline=$5 timeout=${6:-$ASSERT_TIMEOUT}
  local started actual delta verdict
  started=$(date +%s)
  [[ -z "$baseline" ]] && baseline=0

  while :; do
    actual=$(prom_query "$promql" || echo "")
    [[ -z "$actual" ]] && actual=0
    delta=$(awk -v a="$actual" -v b="$baseline" 'BEGIN { print a - b }')
    if compare_num "$delta" "$op" "$expected"; then
      verdict=PASS
      break
    fi
    if (( $(date +%s) - started >= timeout )); then
      verdict=FAIL
      break
    fi
    sleep 5
  done

  record_assertion "$verdict" "$label" "delta $op $expected" "$delta (now $actual, was $baseline)" "$promql"
  [[ "$verdict" == PASS ]]
}

# prom_baseline <promql> — a counter's value now, to compare a scenario against.
# An unscraped counter reads as zero, which is what a counter that has never
# been incremented means anyway.
prom_baseline() {
  local value
  value=$(prom_query "$1" || echo "")
  echo "${value:-0}"
}

# assert_that <label> <expected> <actual> <true|false>
#
# For the claims that are not read from Prometheus: what the status endpoint
# said each partition ended as, and what the coordinator's log shows it did.
assert_that() {
  local label=$1 expected=$2 actual=$3 ok=$4
  local verdict=FAIL
  [[ "$ok" == "true" ]] && verdict=PASS
  record_assertion "$verdict" "$label" "$expected" "$actual" "(read from the cluster)"
  [[ "$verdict" == PASS ]]
}

compare_num() {
  local actual=$1 op=$2 expected=$3
  awk -v a="$actual" -v b="$expected" -v op="$op" 'BEGIN {
    if (op == "gt") ok = (a > b);
    else if (op == "ge") ok = (a >= b);
    else if (op == "lt") ok = (a < b);
    else if (op == "le") ok = (a <= b);
    else if (op == "eq") ok = (a == b);
    else if (op == "ne") ok = (a != b);
    else { print "unknown comparison: " op > "/dev/stderr"; exit 2 }
    exit ok ? 0 : 1
  }'
}

record_assertion() {
  local verdict=$1 label=$2 expected=$3 actual=$4 promql=$5
  printf '%s\t%s\texpected %s\tactual %s\t%s\n' \
    "$verdict" "$label" "$expected" "$actual" "$promql" >> "$SCENARIO_DIR/assertions.tsv"
  if [[ "$verdict" == PASS ]]; then
    log "  PASS  $label — $actual"
  else
    warn "  FAIL  $label — expected $expected, actual $actual"
  fi
}

# --- Named reads the scenarios share --------------------------------------

# How balanced the cluster is, reduced as the dashboard reduces it: pessimistically
# per partition first, since every member answers for every partition from two
# views that converge independently, then averaged across partitions.
BALANCE_QUERY='avg(min by (physicalTenant, partition) (zeebe_cluster_partition_balanced{namespace="$NAMESPACE"}))'

PI_PER_SECOND_QUERY='sum(rate(zeebe_element_instance_events_total{namespace="$NAMESPACE", action="completed", type="PROCESS"}[2m]))'

# The same read over a wider window, for the liveness check. Restarting brokers
# resets the counters and leaves too few samples in a short window for a rate to
# be computed at all, which a narrower window reports as no data rather than as
# a low rate - and a scenario that just restarted a broker would then look like
# a dead workload.
PI_PER_SECOND_WIDE_QUERY='sum(rate(zeebe_element_instance_events_total{namespace="$NAMESPACE", action="completed", type="PROCESS"}[5m]))'

# Writes a transfer rejected. A rejected write reaches the client as a retryable
# RESOURCE_EXHAUSTED, so this is the client-visible cost of a rebalance.
REJECTED_WRITES_QUERY='sum(zeebe_flow_control_total{namespace="$NAMESPACE", outcome="partitionPaused"})'

PAUSED_PARTITIONS_QUERY='max(zeebe_cluster_rebalance_partition_paused{namespace="$NAMESPACE"})'

PAUSE_COUNT_QUERY='sum(zeebe_cluster_rebalance_partition_pause_duration_seconds_count{namespace="$NAMESPACE"})'

TRANSFER_COUNT_QUERY='sum(zeebe_cluster_rebalance_partition_duration_seconds_count{namespace="$NAMESPACE"})'

MAX_LAG_QUERY='max(zeebe_raft_replication_lag_bytes{namespace="$NAMESPACE"})'

# result_query <result> — how many partitions a rebalance resolved with a given
# result, which is the fastest read on why a rebalance did what it did. Every
# partition the coordinator resolved is counted here, including the ones it
# resolved without asking anyone (ALREADY_BALANCED, NO_DESIRED_LEADER).
result_query() {
  echo "sum(zeebe_cluster_rebalance_partition_duration_seconds_count{namespace=\"\$NAMESPACE\", result=\"$1\"})"
}

result_count() {
  prom_query "$(result_query "$1")"
}
