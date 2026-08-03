#!/usr/bin/env bash
# The scenarios a sustained run works through, in order.
#
# Each one leaves the cluster in a state the next can start from, creates its own
# imbalance where it needs one, and asserts on metrics as well as on the status
# endpoint - a scenario whose panels stayed empty has shown nothing, and the run
# is the only chance to find that out. Sourced by run.sh.

SCENARIOS=(
  baseline
  api-surface
  balanced-rebalance
  imbalance-and-rebalance
  saturation-rebalance
  lag-refusal
  lag-timeout
  cancel-mid-rebalance
  coordinator-kill
  broker-down
  leader-wait-timeout
  soak
)

# --- 1. Baseline -----------------------------------------------------------

scenario_baseline() {
  note "a freshly installed cluster has converged on its preferred leaders, so this is the balanced state the imbalances below are created against"
  settle 1200 "the workload to settle and a metrics window to build up"

  assert_num "workload throughput" "$PI_PER_SECOND_QUERY" gt 10
  assert_num "balance gauge published by every member" \
    'count(zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})' eq "$((PARTITIONS * BROKERS))"
  assert_num "cluster is balanced" "$BALANCE_QUERY" eq 1
  assert_num "every partition is active" \
    'count(zeebe_cluster_partition_balanced{namespace="$NAMESPACE"}) / '"$BROKERS" eq "$PARTITIONS"
  assert_absent "no rebalance is running" \
    'zeebe_cluster_rebalance_partition_state{namespace="$NAMESPACE"}'
  assert_absent "nothing is left frozen" \
    'zeebe_cluster_rebalance_partition_paused{namespace="$NAMESPACE"} > 0'

  BASELINE_PI_PER_SECOND=$(prom_query "$PI_PER_SECOND_QUERY")
  note "sustained completion rate is $(printf '%.1f' "$BASELINE_PI_PER_SECOND") PI/s — the ceiling the saturation scenario stays under"
  prom_snapshot "balance-by-partition" \
    'min by (physicalTenant, partition) (zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})'
}

# --- 2. API surface --------------------------------------------------------

scenario_api-surface() {
  local body status broker

  note "no leadership moves in this scenario, so anything that shows up in the Rebalancing row here came from a dry run"

  for broker in $(seq 0 $((BROKERS - 1))); do
    status=$(curl -sS -m 20 -o /dev/null -w '%{http_code}' \
      "http://localhost:$(pf_local_port "$broker")/actuator/cluster/rebalance")
    assert_that "broker $broker answers for the coordinator" "200" "$status" \
      "$([[ "$status" == "200" ]] && echo true || echo false)"
  done

  local paused_before rejected_before
  paused_before=$(prom_baseline "$PAUSE_COUNT_QUERY")
  rejected_before=$(prom_baseline "$REJECTED_WRITES_QUERY")

  reb_run "dry-run" '{"dryRun":true}' 120 >/dev/null
  local dry_run outcome
  dry_run=$(jq -r '.lastCompletedRebalance.dryRun' "$SCENARIO_DIR/dry-run-final.json")
  outcome=$(jq -r '.lastCompletedRebalance.outcome' "$SCENARIO_DIR/dry-run-final.json")
  assert_that "a dry run reports itself as one" "dryRun true, outcome COMPLETED" \
    "dryRun $dry_run, outcome $outcome" \
    "$([[ "$dry_run" == "true" && "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  assert_that "a dry run plans every partition" "$PARTITIONS partitions" \
    "$(jq -r '.lastCompletedRebalance.partitions | length' "$SCENARIO_DIR/dry-run-final.json") partitions" \
    "$([[ "$(jq -r '.lastCompletedRebalance.partitions | length' "$SCENARIO_DIR/dry-run-final.json")" == "$PARTITIONS" ]] && echo true || echo false)"

  # Settings the operator overrode come back, and the ones left out do not,
  # because the values they fall back to belong to each partition's leader. They
  # are reported off the running rebalance, so this needs a real one rather than
  # a dry run, which is over before it can be asked. The cluster is balanced
  # here, so it moves nothing.
  local settings
  settings=$(reb_post '{"replicationLagThreshold":1048576,"replicationTimeout":"PT5S","maxTransferAttempts":5,"leaderWaitTimeout":"PT30S"}' \
    | http_body | jq -c '.settings // {}')
  reb_await 120 >/dev/null || true
  assert_that "overrides are echoed back on the running rebalance" \
    'all four settings' "$settings" \
    "$(jq -e 'has("replicationLagThreshold") and has("replicationTimeout") and has("maxTransferAttempts") and has("leaderWaitTimeout")' <<<"$settings" >/dev/null && echo true || echo false)"
  note "settings are reported only while a rebalance runs, and only what the operator overrode: a dry run, whose purpose is to show what would happen under given settings, reports none"

  for body in '{"replicationTimeout":"5 seconds"}' '{"maxTransferAttempts":0}' '{"replicationLagThreshold":-1}' '{"leaderWaitTimeout":"PT0S"}'; do
    status=$(reb_post "$body" | http_status)
    assert_that "rejects $body" "400" "$status" \
      "$([[ "$status" == "400" ]] && echo true || echo false)"
  done

  local cancelled
  cancelled=$(reb_delete | http_body | jq -r '.wasRunning')
  assert_that "cancelling while idle says nothing was running" "wasRunning false" \
    "wasRunning $cancelled" "$([[ "$cancelled" == "false" ]] && echo true || echo false)"

  settle 60 "a scrape to cover the dry runs"
  assert_delta "a dry run pauses nothing" "$PAUSE_COUNT_QUERY" eq 0 "$paused_before"
  assert_delta "a dry run rejects no writes" "$REJECTED_WRITES_QUERY" eq 0 "$rejected_before"
  assert_num "the cluster is still balanced" "$BALANCE_QUERY" eq 1
}

# --- 3. Rebalancing a balanced cluster ------------------------------------

scenario_balanced-rebalance() {
  local paused_before transferred_before already_before elapsed_before
  paused_before=$(prom_baseline "$PAUSE_COUNT_QUERY")
  transferred_before=$(prom_baseline "$(result_query TRANSFERRED)")
  already_before=$(prom_baseline "$(result_query ALREADY_BALANCED)")
  elapsed_before=$(prom_baseline 'sum(zeebe_cluster_rebalance_elapsed_seconds_count{namespace="$NAMESPACE"})')

  local outcome; outcome=$(reb_run "balanced" '{}' 120)
  note "states: $(reb_outcome_states balanced)"
  assert_that "a balanced cluster rebalances to COMPLETED" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  assert_that "every partition is left alone" "$PARTITIONS skipped" \
    "$(reb_state_count balanced SKIPPED) skipped" \
    "$([[ "$(reb_state_count balanced SKIPPED)" == "$PARTITIONS" ]] && echo true || echo false)"

  settle 60 "a scrape to cover the rebalance"
  assert_delta "the rebalance is counted" \
    'sum(zeebe_cluster_rebalance_elapsed_seconds_count{namespace="$NAMESPACE"})' ge 1 "$elapsed_before"
  assert_delta "nothing was frozen" "$PAUSE_COUNT_QUERY" eq 0 "$paused_before"
  # Every partition is still counted, under the result that says the rebalance
  # resolved it without asking anyone - which is how the outcomes table tells
  # "nothing to do" apart from a transfer that happened.
  assert_delta "no leadership was transferred" "$(result_query TRANSFERRED)" eq 0 "$transferred_before"
  assert_delta "every partition is counted as already balanced" \
    "$(result_query ALREADY_BALANCED)" eq "$PARTITIONS" "$already_before"
  assert_absent "the coordinator's gauges are torn down" \
    'zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"}'
}

# --- 4. Imbalance, then a rebalance under steady load ---------------------

scenario_imbalance-and-rebalance() {
  local since coordinator
  coordinator=$(coordinator_pod)
  create_imbalance 0 3

  assert_num "the cluster is no longer balanced" "$BALANCE_QUERY" lt 1
  local unbalanced; unbalanced=$(verify_unbalanced 120)
  assert_that "leadership drifted off the highest-priority replicas" \
    "at least one partition unbalanced" "$unbalanced" \
    "$(grep -q "lower-priority" <<<"$unbalanced" && echo true || echo false)"
  prom_snapshot "imbalance-by-partition" \
    'min by (physicalTenant, partition) (zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})'

  local paused_before rejected_before transfers_before
  paused_before=$(prom_baseline "$PAUSE_COUNT_QUERY")
  rejected_before=$(prom_baseline "$REJECTED_WRITES_QUERY")
  transfers_before=$(prom_baseline "$TRANSFER_COUNT_QUERY")

  since=$(date -u +%s)
  local outcome; outcome=$(reb_run "rebalance" '{}' 300)
  note "states: $(reb_outcome_states rebalance)"
  assert_that "the rebalance completed" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  assert_that "no partition failed" "0 failed" "$(reb_state_count rebalance FAILED) failed" \
    "$([[ "$(reb_state_count rebalance FAILED)" == "0" ]] && echo true || echo false)"
  assert_that "partitions were actually transferred" "at least one transferred" \
    "$(reb_state_count rebalance TRANSFERRED) transferred" \
    "$(compare_num "$(reb_state_count rebalance TRANSFERRED)" gt 0 && echo true || echo false)"

  # Rebalances neither queue nor merge, since each pins its own view of the
  # desired leaders. The first POST answers as soon as the rebalance is accepted
  # rather than when it ends, so the second lands while the first still runs -
  # usually, because a whole rebalance can be over inside a second.
  local first second
  first=$(reb_post '{"replicationTimeout":"PT20S"}' | http_status)
  second=$(reb_post '{}' | http_status)
  if [[ "$second" == "409" ]]; then
    assert_that "a second rebalance is refused while one runs" "409" "$second" true
  else
    note "the first rebalance ($first) had already finished when the second was sent ($second), so the refusal path was not reached this time"
  fi
  reb_await 300 >/dev/null || true

  assert_serialisation "$coordinator" "$((( $(date -u +%s) - since ) + 60 ))s"

  settle 90 "the two gossiped views to converge and a scrape to land"
  assert_num "the cluster is balanced again" "$BALANCE_QUERY" eq 1 180
  local balanced; balanced=$(verify_balanced 180)
  assert_that "every partition is led by its highest-priority replica" \
    "all balanced" "$balanced" \
    "$(grep -q "Every partition" <<<"$balanced" && echo true || echo false)"

  assert_delta "each transfer froze its partition" "$PAUSE_COUNT_QUERY" gt 0 "$paused_before"
  assert_delta "the coordinator timed each partition it worked on" "$TRANSFER_COUNT_QUERY" gt 0 "$transfers_before"
  assert_num "nothing is left frozen" "$PAUSED_PARTITIONS_QUERY" eq 0
  assert_absent "the coordinator's state gauges are torn down" \
    'zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"}'
  assert_num "the workload recovered" "$PI_PER_SECOND_QUERY" gt 10

  local rejected_delta
  rejected_delta=$(awk -v a="$(prom_baseline "$REJECTED_WRITES_QUERY")" -v b="$rejected_before" 'BEGIN { print a - b }')
  note "writes rejected while partitions were frozen: $rejected_delta — a client sees these as retryable RESOURCE_EXHAUSTED"
  prom_snapshot "transfer-outcomes" \
    'sum by (partition, result) (zeebe_cluster_rebalance_partition_duration_seconds_count{namespace="$NAMESPACE"})'
  prom_snapshot "pause-durations" \
    'sum by (partition) (zeebe_cluster_rebalance_partition_pause_duration_seconds_sum{namespace="$NAMESPACE"})'
}

# Serialisation is the core safety claim, and polling cannot prove it: a whole
# rebalance can be over in a second, so a poll takes few samples of it. The
# coordinator's log is decisive, because it names every ask and every outcome in
# order, and two partitions open at once would show as two asks in a row.
assert_serialisation() {
  local coordinator=$1 since=$2 asks outcomes overlaps
  local lines; lines=$(coordinator_log "$coordinator" "$since" \
    "is asking .* to transfer leadership of|moved leadership of|left leadership of|had its transfer of .* declined|gives up on")
  printf '%s\n' "$lines" > "$SCENARIO_DIR/coordinator-narrative.log"

  asks=$(grep -c "is asking" <<<"$lines" || true)
  outcomes=$(grep -cE "moved leadership of|left leadership of|declined|gives up on" <<<"$lines" || true)
  # Two asks with no outcome between them would mean two partitions open at once.
  overlaps=$(awk '/is asking/ { if (open) overlaps++; open = 1 }
                  /moved leadership of|left leadership of|declined|gives up on/ { open = 0 }
                  END { print overlaps + 0 }' <<<"$lines")

  assert_that "transfers are serialised in the coordinator's log" \
    "0 asks without an outcome before the next" "$overlaps overlaps across $asks asks and $outcomes outcomes" \
    "$([[ "$overlaps" == "0" && "$asks" -gt 0 ]] && echo true || echo false)"

  # Reported per rebalance rather than asserted: the report is a lossy message,
  # so the occasional fallback is by design. On every partition it would mean
  # the leader-to-coordinator path is broken and the rebalance only works by
  # virtue of the topology fallback.
  local fallbacks
  fallbacks=$(coordinator_log "$coordinator" "$since" "without having heard back from the leader it asked" | wc -l | tr -d ' ')
  note "$fallbacks partitions were learned from the topology rather than from the leader's report (out of $asks asked)"
}

# --- 5. Rebalance under saturation ---------------------------------------

scenario_saturation-rebalance() {
  local target
  target=$(awk -v r="${BASELINE_PI_PER_SECOND:-50}" 'BEGIN { print int(r * 0.9) }')
  note "raising load towards ${target} PI/s, which stays under the measured ceiling: the starter's in-flight queue is unbounded, so asking for more than the cluster completes kills it silently"
  starter_scale 2
  settle 420 "the raised load to reach a steady state"
  workload_alive || die "the workload did not survive the raised load, so nothing measured after this would mean anything"

  create_imbalance 1 4
  workload_alive || die "the workload did not survive the imbalance"

  local paused_before rejected_before
  paused_before=$(prom_baseline "$PAUSE_COUNT_QUERY")
  rejected_before=$(prom_baseline "$REJECTED_WRITES_QUERY")
  local lag_before; lag_before=$(prom_query "$MAX_LAG_QUERY" || echo 0)
  note "highest follower replication lag before the rebalance: ${lag_before:-0} bytes, against the 8MB admission threshold"

  local outcome; outcome=$(reb_run "saturated" '{}' 300)
  note "states: $(reb_outcome_states saturated)"
  assert_that "the rebalance completed under saturation" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"

  settle 90 "a scrape to cover the transfers"
  assert_delta "partitions were frozen for their transfers" "$PAUSE_COUNT_QUERY" gt 0 "$paused_before"
  assert_num "no partition is left frozen" "$PAUSED_PARTITIONS_QUERY" eq 0
  assert_num "pauses stayed within the replication timeout" \
    'max(zeebe_cluster_rebalance_partition_pause_duration_seconds_max{namespace="$NAMESPACE"})' lt 10

  local rejected_delta
  rejected_delta=$(awk -v a="$(prom_baseline "$REJECTED_WRITES_QUERY")" -v b="$rejected_before" 'BEGIN { print a - b }')
  note "writes rejected under saturation: $rejected_delta"
  note "$(printf 'LAG_TOO_HIGH transfers so far: %s' "$(result_count LAG_TOO_HIGH || echo 0)")"

  assert_num "the cluster is balanced again" "$BALANCE_QUERY" eq 1 240
  workload_alive || warn "the workload did not survive the saturated rebalance"

  starter_scale 1
  settle 300 "the load to fall back to the baseline"
}

# --- 6. Manufactured replication lag, refused at admission ---------------

scenario_lag-refusal() {
  create_imbalance 2 5
  local desired; desired=$(next_desired_leader)
  note "injecting lag on camunda-$desired, which is the desired leader of a partition the rebalance wants to move"
  inject_lag "$desired" || { note "lag injection is unavailable, so this scenario proves nothing"; return 0; }

  settle 240 "the lag to build past the admission threshold"
  local lag; lag=$(prom_query "$MAX_LAG_QUERY" || echo 0)
  note "highest follower replication lag after injection: ${lag:-0} bytes"
  assert_num "the lag the admission check reads is visible" "$MAX_LAG_QUERY" gt 0

  local paused_before
  paused_before=$(prom_baseline "$PAUSE_COUNT_QUERY")
  local outcome; outcome=$(reb_run "lagging" '{}' 300)
  note "states: $(reb_outcome_states lagging)"
  note "reasons: $(jq -r '[(.lastCompletedRebalance.partitions // [])[] | select(.reason != null) | "\(.id):\(.reason)"] | join(" ")' "$SCENARIO_DIR/lagging-final.json")"

  # Either outcome is acceptable and which one happened is the point: the
  # admission check refuses the transfer, or the catch-up wait covers the lag.
  assert_that "the rebalance resolved every partition it took on" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  local failed; failed=$(reb_state_count lagging FAILED)
  if [[ "$failed" -gt 0 ]]; then
    note "$failed partitions were refused, which is the LAG_TOO_HIGH path being exercised end to end for the first time"
    assert_num "a refused transfer froze nothing" "$PAUSED_PARTITIONS_QUERY" eq 0
  else
    note "every transfer was still admitted, so the catch-up wait covered the lag rather than the admission check refusing it"
    assert_delta "the catch-up wait shows up as a longer pause" "$PAUSE_COUNT_QUERY" gt 0 "$paused_before"
  fi

  prom_snapshot "lag-by-follower" \
    'max by (partition, follower) (zeebe_raft_replication_lag_bytes{namespace="$NAMESPACE"})'
  prom_snapshot "transfer-results" \
    'sum by (result) (zeebe_cluster_rebalance_partition_duration_seconds_count{namespace="$NAMESPACE"})'

  clear_lag
  settle 180 "replication to catch up once the lag is cleared"
  assert_num "the lag falls back once the delay is removed" "$MAX_LAG_QUERY" lt 8388608 240
}

# --- 7. A catch-up that cannot finish in time ---------------------------

scenario_lag-timeout() {
  local desired
  create_imbalance 0 2
  desired=$(next_desired_leader)
  inject_lag "$desired" || { note "lag injection is unavailable, so this scenario proves nothing"; return 0; }
  settle 180 "the lag to build"

  # The single most valuable observation available: the only end-to-end proof
  # that a pause is bounded by something other than the happy path finishing
  # quickly. A replication timeout short enough that the catch-up cannot make it.
  local outcome
  outcome=$(reb_run "timed-out" '{"replicationTimeout":"PT1S","replicationLagThreshold":1073741824}' 300)
  note "states: $(reb_outcome_states timed-out)"
  note "reasons: $(jq -r '[(.lastCompletedRebalance.partitions // [])[] | select(.reason != null) | "\(.id):\(.reason)"] | join(" ")' "$SCENARIO_DIR/timed-out-final.json")"

  assert_that "the rebalance resolved every partition" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  assert_num "the pause ended, whether the catch-up finished or gave up" "$PAUSED_PARTITIONS_QUERY" eq 0 180
  note "$(printf 'REPLICATION_TIMED_OUT transfers so far: %s' "$(result_count REPLICATION_TIMED_OUT || echo 0)")"
  assert_num "the workload is still being served" "$PI_PER_SECOND_QUERY" gt 1

  clear_lag
  settle 180 "replication to catch up"
  reb_run "recovery" '{}' 300 >/dev/null
  assert_num "a later rebalance still transfers, so no leader is stuck refusing" "$BALANCE_QUERY" eq 1 240
}

# --- 8. Cancelling mid-rebalance ---------------------------------------

scenario_cancel-mid-rebalance() {
  local desired
  create_imbalance 1 3 5
  desired=$(next_desired_leader)
  # A transfer is tens of milliseconds at native speed, so a cancellation aimed
  # at one is chance rather than method. Lag widens the window to aim at.
  inject_lag "$desired" || note "lag injection is unavailable, so the cancellation may land after the rebalance has finished"
  settle 120 "the lag to widen the transfer window"

  local response status
  response=$(reb_post '{"replicationTimeout":"PT20S"}')
  status=$(http_status <<<"$response")
  assert_that "the rebalance was accepted" "202" "$status" \
    "$([[ "$status" == "202" ]] && echo true || echo false)"

  sleep 2
  local cancelled; cancelled=$(reb_delete | http_body | jq -r '.wasRunning')
  note "cancellation reported wasRunning=$cancelled"
  reb_await 300 "$SCENARIO_DIR/cancel-poll.jsonl" || true
  reb_capture "cancelled" >/dev/null
  local outcome; outcome=$(jq -r '.lastCompletedRebalance.outcome' "$SCENARIO_DIR/cancelled.json")
  note "states: $(jq -r '(.lastCompletedRebalance.partitions // []) | group_by(.status)[] | "\(.[0].status)=\(length)"' "$SCENARIO_DIR/cancelled.json" | paste -sd' ' -)"

  if [[ "$cancelled" == "true" ]]; then
    assert_that "a cancelled rebalance ends CANCELLED" "CANCELLED" "$outcome" \
      "$([[ "$outcome" == "CANCELLED" ]] && echo true || echo false)"
  else
    note "the rebalance had already finished when the cancellation arrived, so this run tested the idle path instead — a wider transfer window is needed to test cancellation"
  fi

  assert_num "nothing is left frozen after cancelling" "$PAUSED_PARTITIONS_QUERY" eq 0 180
  assert_absent "the coordinator's gauges are torn down after cancelling" \
    'zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"}'
  assert_num "the workload is still being served" "$PI_PER_SECOND_QUERY" gt 1

  clear_lag
  settle 120 "replication to catch up"
  reb_run "after-cancel" '{}' 300 >/dev/null
  assert_num "a rebalance after a cancelled one still converges" "$BALANCE_QUERY" eq 1 240
}

# --- 9. Losing the coordinator mid-rebalance --------------------------

scenario_coordinator-kill() {
  local coordinator desired
  create_imbalance 2 4
  desired=$(next_desired_leader)
  inject_lag "$desired" || note "lag injection is unavailable, so the kill may land after the rebalance has finished"
  settle 120 "the lag to widen the transfer window"

  coordinator=$(coordinator_pod)
  note "coordinating member is $coordinator"
  assert_num "only one member publishes the coordinator's gauges before the kill" \
    'count(count by (pod) (zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"})) or vector(0)' le 1

  reb_post '{"replicationTimeout":"PT20S"}' >/dev/null
  sleep 3
  kill_pod "$coordinator"

  await_brokers_ready || true
  pf_check
  settle 120 "the configuration to name a new coordinator"

  local status; status=$(reb_get | jq -r '.status')
  note "the surviving coordinator reports status $status with no history, which is by design: coordinator state is held in memory"

  # The release-blocking check. A partition left frozen after its coordinator
  # went away would be an outage no operator asked for.
  assert_num "no partition is left frozen after the coordinator went away" \
    "$PAUSED_PARTITIONS_QUERY" eq 0 240
  assert_num "the workload is still being served" "$PI_PER_SECOND_QUERY" gt 1
  assert_absent "no member is still publishing gauges for the abandoned rebalance" \
    'zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"}' 180
  prom_snapshot "pending-gauge-by-pod" \
    'count by (pod) (zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"})'
  assert_num "every member still publishes the balance gauge" \
    'count(zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})' eq "$((PARTITIONS * BROKERS))" 240

  clear_lag
  settle 120 "replication to catch up"
  local outcome; outcome=$(reb_run "after-kill" '{}' 300)
  assert_that "a rebalance after the coordinator moved still works" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  assert_num "the cluster is balanced again" "$BALANCE_QUERY" eq 1 240
}

# --- 10. Rebalancing with a broker missing ---------------------------

scenario_broker-down() {
  create_imbalance 0 1

  # Scaling the StatefulSet down does not remove the member from the cluster
  # configuration, so it is still the highest-priority member of its partitions
  # and there is still somewhere the rebalance wants leadership to go. Those
  # partitions must therefore be reported FAILED rather than SKIPPED: an
  # operator, or any automation polling the endpoint, has to be able to tell
  # "tried and could not" from "nothing to do". This was a real defect once.
  local gone=$((BROKERS - 1))
  kubectl scale statefulset/camunda -n "$NS" --replicas="$gone" >/dev/null
  log "  scaled the StatefulSet to $gone, so camunda-$gone is gone while staying in the configuration"
  settle 120 "the cluster to notice the member is unreachable"

  local outcome; outcome=$(reb_run "broker-down" '{}' 300)
  note "states: $(reb_outcome_states broker-down)"
  note "reasons: $(jq -r '[(.lastCompletedRebalance.partitions // [])[] | select(.reason != null) | "\(.id):\(.reason)"] | join(" ")' "$SCENARIO_DIR/broker-down-final.json")"

  assert_that "the rebalance carries on through the partitions it can move" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  local failed; failed=$(reb_state_count broker-down FAILED)
  assert_that "a partition it wanted to move and could not is FAILED, not SKIPPED" \
    "at least one FAILED" "$failed failed, $(reb_state_count broker-down SKIPPED) skipped" \
    "$(compare_num "$failed" gt 0 && echo true || echo false)"
  assert_num "nothing is left frozen" "$PAUSED_PARTITIONS_QUERY" eq 0
  prom_snapshot "failed-transfer-results" \
    'sum by (partition, result) (zeebe_cluster_rebalance_partition_duration_seconds_count{namespace="$NAMESPACE"})'

  kubectl scale statefulset/camunda -n "$NS" --replicas="$BROKERS" >/dev/null
  await_brokers_ready || true
  pf_check
  settle 120 "the returning member to rejoin"
  reb_run "broker-back" '{}' 300 >/dev/null
  assert_num "the cluster is balanced once the member is back" "$BALANCE_QUERY" eq 1 240
}

# --- 11. Giving up on a leader too early ----------------------------

scenario_leader-wait-timeout() {
  local desired
  create_imbalance 3 5
  desired=$(next_desired_leader)
  inject_lag "$desired" || note "lag injection is unavailable, so the leader may answer before the wait expires"
  settle 120 "the lag to slow the transfer down"

  # The documented hazard: the coordinator gives up while the leader is still
  # working. What matters is whether the partition is left frozen, and for how
  # long - and the coordinator's gauges are gone by the end of the rebalance, so
  # this is read from the leader-published pause gauge, which is not.
  local outcome
  outcome=$(reb_run "leader-silent" '{"leaderWaitTimeout":"PT1S","replicationTimeout":"PT30S"}' 300)
  note "states: $(reb_outcome_states leader-silent)"
  note "reasons: $(jq -r '[(.lastCompletedRebalance.partitions // [])[] | select(.reason != null) | "\(.id):\(.reason)"] | join(" ")' "$SCENARIO_DIR/leader-silent-final.json")"
  note "$(printf 'LEADER_SILENT transfers so far: %s' "$(result_count LEADER_SILENT || echo 0)")"

  assert_that "the rebalance still resolves every partition" "COMPLETED" "$outcome" \
    "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
  assert_num "the pause ends even though the coordinator stopped waiting" \
    "$PAUSED_PARTITIONS_QUERY" eq 0 240
  assert_num "the workload is still being served" "$PI_PER_SECOND_QUERY" gt 1
  prom_snapshot "pause-duration-buckets" \
    'sum by (le) (zeebe_cluster_rebalance_partition_pause_duration_seconds_bucket{namespace="$NAMESPACE"})'

  clear_lag
  settle 120 "replication to catch up"
  reb_run "after-silent" '{}' 300 >/dev/null
  assert_num "the cluster is balanced again" "$BALANCE_QUERY" eq 1 240
}

# --- 12. Soak ------------------------------------------------------

scenario_soak() {
  local iterations=${SOAK_ITERATIONS:-9} interval=300 i outcome
  note "rebalancing every $(scaled $interval)s for $iterations iterations, restarting a broker every third, watching for state and series leaks"

  for i in $(seq 1 "$iterations"); do
    log "  soak iteration $i/$iterations"
    if (( i % 3 == 0 )); then
      create_imbalance "$(( (i / 3) % BROKERS ))"
    fi
    outcome=$(reb_run "soak-$i" '{}' 300)
    log "  soak iteration $i ended $outcome: $(reb_outcome_states "soak-$i")"

    # A leader that refuses every later transfer with TRANSFER_IN_PROGRESS is the
    # leak class of bug this loop exists to find, and it only shows as late
    # rebalances that stop transferring anything.
    assert_that "soak iteration $i resolved every partition" "COMPLETED" "$outcome" \
      "$([[ "$outcome" == "COMPLETED" ]] && echo true || echo false)"
    assert_num "soak iteration $i left nothing frozen" "$PAUSED_PARTITIONS_QUERY" eq 0 120
    assert_absent "soak iteration $i left no coordinator gauges behind" \
      'zeebe_cluster_rebalance_partition_pending{namespace="$NAMESPACE"}' 120
    assert_num "soak iteration $i kept the balance gauge series count" \
      'count(zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})' eq "$((PARTITIONS * BROKERS))" 180
    workload_alive || warn "  the workload is not alive at soak iteration $i"
    settle "$interval" "the next soak iteration"
  done

  assert_num "the cluster ends balanced" "$BALANCE_QUERY" eq 1 300
  assert_num "the coordinator's heap did not run away" \
    'max(jvm_memory_used_bytes{namespace="$NAMESPACE", area="heap"}) / max(jvm_memory_max_bytes{namespace="$NAMESPACE", area="heap"})' lt 0.95
  prom_snapshot "soak-transfer-results" \
    'sum by (result) (zeebe_cluster_rebalance_partition_duration_seconds_count{namespace="$NAMESPACE"})'
  prom_snapshot "soak-balance-series" \
    'count by (pod) (zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})'
}

# --- Shared ------------------------------------------------------

# next_desired_leader — a broker a rebalance would transfer a partition to, read
# from a dry run's plan so that lag is injected where it will actually matter.
# A dry run's whole product is its plan, so it completes immediately and the plan
# arrives under lastCompletedRebalance rather than in the accepting response.
next_desired_leader() {
  local broker
  reb_post '{"dryRun":true}' >/dev/null
  broker=$(reb_get | jq -r '[(.lastCompletedRebalance.partitions // [])[]
                             | select(.status == "PENDING") | .desiredLeader] | first // empty')
  if [[ -z "$broker" ]]; then
    # Nothing to transfer, so any broker other than the one leading everything
    # will do to demonstrate lag.
    broker=$((BROKERS - 1))
  fi
  echo "$broker"
}
