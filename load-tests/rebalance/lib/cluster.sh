#!/usr/bin/env bash
# Talking to the cluster: port forwards, the rebalance endpoint, the workload,
# and the faults the scenarios inject.
#
# Sourced by run.sh; not runnable on its own.

# --- Port forwards ---------------------------------------------------------

# A three-hour run outlives its port forwards, so every one of them is checked
# before use and respawned when it has died. Brokers are addressed individually
# because the scenarios need a specific broker: the coordinator, or one
# partition's leader.
declare -a PF_PIDS=()

pf_local_port() { echo $((PF_BASE_PORT + $1)); }

pf_start_broker() {
  local broker=$1 port
  port=$(pf_local_port "$broker")
  kubectl port-forward -n "$NS" "camunda-$broker" "$port:9600" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
}

pf_start_prometheus() {
  kubectl port-forward -n "$PROM_NAMESPACE" "svc/$PROM_SERVICE" "$PROM_PORT:9090" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
}

pf_start_all() {
  local broker
  # A forward left behind by an earlier run holds the local port, so the new
  # one silently fails to bind and every query through it fails - which reads as
  # a dead cluster rather than as a dead tunnel.
  pf_kill_stale
  for broker in $(seq 0 $((BROKERS - 1))); do pf_start_broker "$broker"; done
  pf_start_prometheus
  pf_wait_ready
}

pf_kill_stale() {
  pkill -f "kubectl port-forward -n $NS camunda-" >/dev/null 2>&1 || true
  pkill -f "kubectl port-forward -n $PROM_NAMESPACE svc/$PROM_SERVICE" >/dev/null 2>&1 || true
  sleep 1
}

# pf_wait_ready — the forwards are only usable once they answer, and how long
# that takes varies, so this waits for the answer rather than for a fixed time.
pf_wait_ready() {
  local timeout=${1:-60} started broker port pending
  started=$(date +%s)
  while :; do
    pending=""
    for broker in $(seq 0 $((BROKERS - 1))); do
      port=$(pf_local_port "$broker")
      curl -sS -m 5 -o /dev/null "http://localhost:$port/actuator/health" 2>/dev/null || pending="$pending camunda-$broker"
    done
    curl -sS -m 5 -o /dev/null "$PROM_URL/-/healthy" 2>/dev/null || pending="$pending prometheus"
    [[ -z "$pending" ]] && return 0
    if (( $(date +%s) - started >= timeout )); then
      warn "  port forwards not answering after ${timeout}s:$pending"
      return 1
    fi
    sleep 3
  done
}

pf_stop_all() {
  local pid
  for pid in "${PF_PIDS[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" >/dev/null 2>&1 || true
  done
  PF_PIDS=()
  # Also by pattern, so that a forward whose pid was lost - a respawn, or an
  # interrupted run - does not leak into the next run's ports.
  pf_kill_stale
}

# pf_check — restarts whatever has stopped answering. Called at every scenario
# boundary, and after anything that restarts a broker pod, which always kills
# its forward.
pf_check() {
  if ! pf_wait_ready 5 >/dev/null 2>&1; then
    log "  restarting port forwards"
    pf_stop_all
    pf_start_all || warn "  port forwards are still not answering"
  fi
}

# --- The rebalance endpoint ------------------------------------------------

# The POST and DELETE helpers append the status code on its own line, so that a
# caller can assert on it as well as on the body. Splitting the two goes through
# these, because `head -n -1` is GNU-only and this run is driven from a laptop.
http_body() { sed '$d'; }
http_status() { tail -1; }

# reb_get [broker] — the rebalance status as the given broker reports it. Any
# broker forwards to whichever member coordinates, so broker 0 is the default
# and another is passed only to prove the forwarding.
reb_get() {
  local broker=${1:-0}
  curl -sS -m 20 "http://localhost:$(pf_local_port "$broker")/actuator/cluster/rebalance"
}

# reb_post <json body> [broker] — triggers a rebalance, printing `body\nstatus`
# so a caller can assert on the status code as well as the body.
reb_post() {
  local body=$1 broker=${2:-0}
  curl -sS -m 20 -w '\n%{http_code}' -X POST \
    -H 'Content-Type: application/json' -d "$body" \
    "http://localhost:$(pf_local_port "$broker")/actuator/cluster/rebalance"
}

reb_delete() {
  local broker=${1:-0}
  curl -sS -m 20 -w '\n%{http_code}' -X DELETE \
    "http://localhost:$(pf_local_port "$broker")/actuator/cluster/rebalance"
}

# reb_await <timeout_s> [poll_file] — polls until the coordinator reports itself
# idle. When given a file, every sample is appended to it: a whole rebalance can
# be over inside a second, so the sample count is part of the evidence and is
# reported alongside whatever the samples showed.
reb_await() {
  local timeout=$1 poll_file=${2:-} started samples status
  started=$(date +%s)
  samples=0
  while :; do
    local body; body=$(reb_get 2>/dev/null || echo '{}')
    status=$(jq -r '.status // "UNKNOWN"' <<<"$body")
    samples=$((samples + 1))
    if [[ -n "$poll_file" ]]; then
      jq -c --arg at "$(date -u +%H:%M:%S.%3N 2>/dev/null || date -u +%H:%M:%S)" \
        '{at: $at, status, rebalanceId,
          transferring: [(.partitions // [])[] | select(.status == "TRANSFERRING") | .id],
          counts: (reduce (.partitions // [])[] as $p ({}; .[$p.status] += 1))}' \
        <<<"$body" >> "$poll_file"
    fi
    [[ "$status" == "IDLE" ]] && break
    if (( $(date +%s) - started >= timeout )); then
      warn "  rebalance did not finish within ${timeout}s, last status $status"
      return 1
    fi
  done
  log "  rebalance finished after $(( $(date +%s) - started ))s over $samples status samples"
}

# reb_capture <name> — the status body, kept because the coordinator's gauges are
# torn down when a rebalance ends, so this is the only place each partition's
# terminal state survives.
reb_capture() {
  reb_get | jq '.' > "$SCENARIO_DIR/$1.json"
  jq -c '{status, last: (.lastCompletedRebalance | {rebalanceId, outcome, dryRun,
          states: (reduce (.partitions // [])[] as $p ({}; .[$p.status] += 1))})}' \
    "$SCENARIO_DIR/$1.json"
}

# reb_run <name> <body> [timeout_s] — the whole cycle a scenario repeats:
# trigger, follow, capture. Prints the completed rebalance's outcome.
reb_run() {
  local name=$1 body=${2:-'{}'} timeout=${3:-300}
  local response status
  response=$(reb_post "$body")
  status=$(http_status <<<"$response")
  http_body <<<"$response" | jq '.' > "$SCENARIO_DIR/$name-accepted.json"
  log "  POST rebalance $body -> $status"
  if [[ "$status" != "202" ]]; then
    warn "  rebalance was not accepted: $(http_body <<<"$response")"
    return 1
  fi
  reb_await "$timeout" "$SCENARIO_DIR/$name-poll.jsonl" || true
  reb_capture "$name-final" >/dev/null
  jq -r '.lastCompletedRebalance.outcome // "NONE"' "$SCENARIO_DIR/$name-final.json"
}

# reb_outcome_states <name> — a count per terminal state from a captured body,
# for asserting on what a rebalance made of its partitions.
reb_outcome_states() {
  jq -r '(.lastCompletedRebalance.partitions // []) | group_by(.status)[] | "\(.[0].status)=\(length)"' \
    "$SCENARIO_DIR/$1-final.json" | paste -sd' ' -
}

reb_state_count() {
  jq -r --arg state "$2" '[(.lastCompletedRebalance.partitions // [])[] | select(.status == $state)] | length' \
    "$SCENARIO_DIR/$1-final.json"
}

# --- Leadership ------------------------------------------------------------

# zbchaos owns the leadership question, so that the same derivation is used here
# and in the chaos experiments rather than a second one that could disagree.
zbc() {
  "$ZBCHAOS" "$@" -n "$NS" 2>&1
}

verify_balanced() {
  local timeout=${1:-120}
  zbc verify leadership --timeoutInSec "$timeout" | tee -a "$SCENARIO_DIR/leadership.log" | tail -1
}

verify_unbalanced() {
  local timeout=${1:-120}
  zbc verify leadership --expectUnbalanced --timeoutInSec "$timeout" \
    | tee -a "$SCENARIO_DIR/leadership.log" | tail -1
}

# --- Cluster health --------------------------------------------------------

healthy_partitions() {
  curl -sS -m 20 "http://localhost:$(pf_local_port 0)/actuator/cluster" \
    | jq '[.brokers[].partitions[] | select(.state == "ACTIVE")] | length'
}

await_brokers_ready() {
  local timeout=${1:-600} started ready
  started=$(date +%s)
  while :; do
    ready=$(kubectl get pods -n "$NS" -l app.kubernetes.io/component=zeebe-broker \
      --no-headers 2>/dev/null | awk '$2 == "1/1" && $3 == "Running"' | wc -l | tr -d ' ')
    [[ "$ready" == "$BROKERS" ]] && break
    if (( $(date +%s) - started >= timeout )); then
      warn "  only $ready/$BROKERS brokers ready after ${timeout}s"
      return 1
    fi
    sleep 10
  done
  log "  all $BROKERS brokers ready"
}

# workload_alive — the check the previous run learned the hard way. The starter's
# in-flight queue is unbounded, so asked for more than the cluster completes it
# grows until the JVM dies, and the workload then ends silently: no failed pod,
# just a flat line. A phase measured against a dead workload is void, not soft,
# so this gates every scenario.
workload_alive() {
  local pods restarts oom rate started timeout=${1:-90}
  pods=$(kubectl get pods -n "$NS" -l app=starter --no-headers 2>/dev/null)
  if [[ -z "$pods" ]]; then
    warn "  no starter pod found"
    return 1
  fi
  restarts=$(awk '{ sum += $4 } END { print sum + 0 }' <<<"$pods")
  oom=$(kubectl logs -n "$NS" -l app=starter --tail=200 2>/dev/null | grep -ci "outofmemory" || true)
  if [[ "$oom" -gt 0 ]]; then
    warn "  the starter reported OutOfMemoryError — the workload is dead and will not recover"
    return 1
  fi

  # Retried, because throughput dips while a restarted broker's partitions find
  # their leaders again: a dip is recovery, and only a dip that does not end is a
  # dead workload.
  started=$(date +%s)
  while :; do
    rate=$(prom_query "$PI_PER_SECOND_WIDE_QUERY" || echo "")
    if [[ -n "$rate" ]] && compare_num "$rate" gt 0.5; then
      log "  workload alive: $(printf '%.1f' "$rate") PI/s, $restarts starter restarts"
      return 0
    fi
    if (( $(date +%s) - started >= timeout )); then
      warn "  process instances are completing at ${rate:-no} per second after ${timeout}s — the workload is not alive"
      return 1
    fi
    sleep 10
  done
}

starter_scale() {
  kubectl scale deployment/starter -n "$NS" --replicas="$1" >/dev/null
  log "  starter scaled to $1 replica(s)"
}

# workload_recover — puts the load back to the baseline and restarts the starter,
# for when raising the rate killed it. Asking for more than the cluster completes
# grows an unbounded in-flight queue until the JVM dies, and the workload then
# ends silently; every scenario after that would be measuring nothing, so this is
# worth one recovery attempt rather than abandoning the run.
workload_recover() {
  warn "  the workload is not alive: dropping back to one starter and restarting it"
  starter_scale 1
  kubectl rollout restart deployment/starter -n "$NS" >/dev/null 2>&1 || true
  kubectl rollout status deployment/starter -n "$NS" --timeout=300s >/dev/null 2>&1 || true
  settle 240 "the workload to pick up again"
  workload_alive 180
}

# --- Faults ----------------------------------------------------------------

# create_imbalance <broker...> — restarts brokers so that leadership migrates
# away from them and stays away, since nothing moves it back after a restart.
# That is the reason the feature exists, and the state every scenario needs.
create_imbalance() {
  log "  restarting brokers $* to move leadership away from them"
  kubectl delete pod -n "$NS" $(printf 'camunda-%s ' "$@") --wait=false >/dev/null
  sleep 20
  await_brokers_ready || true
  pf_check
  settle 60 "leadership and the configuration to converge after the restarts"
}

# netem_exec <broker> <tc arguments> — runs tc against a broker's network.
#
# The Camunda image carries no tc and cannot install one, so this goes through an
# ephemeral container that has it. Containers in a pod share a network namespace,
# so a qdisc it installs on eth0 applies to the broker. The security context is
# spelled out because the pod requires a non-root user and tc needs root: the
# sysadmin debug profile alone is refused for having a non-numeric one.
netem_exec() {
  local broker=$1 tc_args=$2 container pod
  pod="camunda-$broker"
  container="netem-$(date +%s)"

  if [[ ! -f "$RUN_DIR/netem-container.json" ]]; then
    cat > "$RUN_DIR/netem-container.json" <<'EOF'
{
  "securityContext": {
    "privileged": true,
    "runAsUser": 0,
    "runAsGroup": 0,
    "runAsNonRoot": false,
    "capabilities": {"add": ["NET_ADMIN"]}
  }
}
EOF
  fi

  kubectl debug -n "$NS" "$pod" --image="$NETEM_IMAGE" --target="$BROKER_CONTAINER" \
    --custom="$RUN_DIR/netem-container.json" --container="$container" -q \
    -- sh -c "tc $tc_args" >/dev/null 2>&1

  local started=$(date +%s) state
  while :; do
    state=$(kubectl get pod "$pod" -n "$NS" -o json 2>/dev/null \
      | jq -r --arg c "$container" '.status.ephemeralContainerStatuses[]? | select(.name == $c) | .state | keys[0]')
    [[ "$state" == "terminated" ]] && break
    if (( $(date +%s) - started >= 120 )); then
      warn "  the tc container on $pod did not finish within 120s"
      return 1
    fi
    sleep 3
  done

  local output; output=$(kubectl logs -n "$NS" "$pod" -c "$container" 2>&1 | tr '\n' ' ')
  local code; code=$(kubectl get pod "$pod" -n "$NS" -o json 2>/dev/null \
    | jq -r --arg c "$container" '.status.ephemeralContainerStatuses[]? | select(.name == $c) | .state.terminated.exitCode')
  if [[ "$code" != "0" ]]; then
    warn "  tc $tc_args on $pod exited $code: $output"
    return 1
  fi
  log "  tc $tc_args on $pod: ${output:-ok}"
}

# inject_lag <broker> — delays a broker's replication traffic, so a partition it
# is the desired leader of falls behind. This hardware otherwise keeps up: a
# broker down for two minutes under saturation caught up within twelve seconds,
# and forcing the threshold to zero admits every transfer anyway because the gate
# is lag > threshold and a follower at genuinely zero lag passes it. The lag has
# to be manufactured, or the lag half of the design goes untested.
inject_lag() {
  local broker=$1
  log "  injecting ${NETEM_DELAY} delay and ${NETEM_LOSS} loss on camunda-$broker"
  netem_exec "$broker" "qdisc add dev eth0 root netem delay $NETEM_DELAY loss $NETEM_LOSS" || {
    warn "  could not inject lag on camunda-$broker"
    return 1
  }
  LAGGING_BROKER=$broker
}

clear_lag() {
  local broker=${1:-${LAGGING_BROKER:-}}
  [[ -z "$broker" ]] && return 0
  LAGGING_BROKER=""
  netem_exec "$broker" "qdisc del dev eth0 root" || \
    warn "  could not clear the injected lag on camunda-$broker; check it before measuring anything else here"
}

# coordinator_pod — the lowest-id member of the committed configuration, which is
# where the coordinator runs and so the pod to kill to abandon a rebalance.
coordinator_pod() {
  local id
  id=$(curl -sS -m 20 "http://localhost:$(pf_local_port 0)/actuator/cluster" \
    | jq -r '[.brokers[] | select(.state == "ACTIVE") | .id] | min')
  echo "camunda-$id"
}

kill_pod() {
  log "  force deleting $1"
  kubectl delete pod -n "$NS" "$1" --grace-period=0 --force >/dev/null 2>&1 || true
}

# coordinator_log <since> <pattern> — the coordinator narrates its whole
# rebalance at INFO, so serialisation and the report path are read from its log
# rather than inferred from polling.
coordinator_log() {
  local pod=$1 since=$2 pattern=$3 raw messages
  raw=$(kubectl logs -n "$NS" "$pod" --since="$since" 2>/dev/null)
  # The broker logs JSON, but the container's startup lines are plain text, so
  # each line is parsed on its own - jq would otherwise abort on the first one
  # and report the whole log as empty.
  messages=$(jq -R -r 'fromjson? | .message // empty' <<<"$raw")
  [[ -z "$messages" ]] && messages=$raw
  grep -E "$pattern" <<<"$messages" || true
}
