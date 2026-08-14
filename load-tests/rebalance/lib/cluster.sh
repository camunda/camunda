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

# The rebalance endpoint lives on the REST Gateway (v2 API), not on the
# actuator/monitoring port `pf_local_port` forwards - a separate container port
# needs its own forward.
pf_local_port_rest() { echo $((REST_PF_BASE_PORT + $1)); }

pf_start_broker() {
  local broker=$1 port rest_port
  port=$(pf_local_port "$broker")
  rest_port=$(pf_local_port_rest "$broker")
  kubectl port-forward -n "$NS" "camunda-$broker" "$port:9600" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
  kubectl port-forward -n "$NS" "camunda-$broker" "$rest_port:8080" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
}

pf_start_prometheus() {
  kubectl port-forward -n "$PROM_NAMESPACE" "svc/$PROM_SERVICE" "$PROM_PORT:9090" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
}

# Keycloak's token endpoint is only on the cluster's internal DNS, and an OIDC
# access token is typically minutes-lived - shorter than even a scaled-down
# shakedown - so `rest_auth_args` mints a fresh one before every request rather
# than trusting one to survive the run. That needs this forward kept alive the
# same way the broker and Prometheus ones are. Only started when a client to
# mint tokens for is configured; a static bearer token or basic auth needs no
# forward here.
pf_start_keycloak() {
  [[ -n "${CLUSTER_ADMIN_CLIENT_ID:-}" ]] || return 0
  kubectl port-forward -n "$KEYCLOAK_NAMESPACE" "svc/$KEYCLOAK_SERVICE" "$KEYCLOAK_PF_PORT:$KEYCLOAK_PORT" \
    >/dev/null 2>&1 &
  PF_PIDS+=("$!")
  CLUSTER_ADMIN_TOKEN_URL="http://localhost:$KEYCLOAK_PF_PORT/auth/realms/$KEYCLOAK_REALM/protocol/openid-connect/token"
}

pf_start_all() {
  local broker
  # A forward left behind by an earlier run holds the local port, so the new
  # one silently fails to bind and every query through it fails - which reads as
  # a dead cluster rather than as a dead tunnel.
  pf_kill_stale
  for broker in $(seq 0 $((BROKERS - 1))); do pf_start_broker "$broker"; done
  pf_start_prometheus
  pf_start_keycloak
  pf_wait_ready
}

pf_kill_stale() {
  pkill -f "kubectl port-forward -n $NS camunda-" >/dev/null 2>&1 || true
  pkill -f "kubectl port-forward -n $PROM_NAMESPACE svc/$PROM_SERVICE" >/dev/null 2>&1 || true
  [[ -n "${CLUSTER_ADMIN_CLIENT_ID:-}" ]] && \
    pkill -f "kubectl port-forward -n $KEYCLOAK_NAMESPACE svc/$KEYCLOAK_SERVICE" >/dev/null 2>&1 || true
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
      # The REST port has no unauthenticated health check, so any response at
      # all (even 401) proves the forward is up; only a connection failure
      # means it is not.
      curl -sS -m 5 -o /dev/null "http://localhost:$(pf_local_port_rest "$broker")/cluster/v2/rebalance" 2>/dev/null || \
        pending="$pending camunda-$broker(rest)"
    done
    curl -sS -m 5 -o /dev/null "$PROM_URL/-/healthy" 2>/dev/null || pending="$pending prometheus"
    if [[ -n "${CLUSTER_ADMIN_CLIENT_ID:-}" ]]; then
      curl -sS -m 5 -o /dev/null "http://localhost:$KEYCLOAK_PF_PORT/auth/realms/$KEYCLOAK_REALM" 2>/dev/null || \
        pending="$pending keycloak"
    fi
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

# The endpoint sits behind the cluster-admin security chain, which does not
# accept an Orchestration Cluster user's credentials. Which auth this chain
# accepts depends on the cluster's authentication method, and the two are
# mutually exclusive - only one chain is ever registered:
#   - method: basic  -> CLUSTER_ADMIN_USER/CLUSTER_ADMIN_PASSWORD, sent as HTTP
#     basic auth, checked against camunda.security.cluster-admin.basic.users.
#   - method: oidc    -> a bearer token whose client_id/group/claim matches
#     camunda.security.cluster-admin.oidc.* (deny-all by default, not "auth
#     disabled" - that config has to name a matcher before any token is
#     accepted). Either pass one directly via CLUSTER_ADMIN_BEARER_TOKEN, or -
#     because an access token is typically minutes-lived and even a scaled-down
#     shakedown outlives that, let alone the full run - set CLUSTER_ADMIN_TOKEN_URL,
#     CLUSTER_ADMIN_CLIENT_ID and CLUSTER_ADMIN_CLIENT_SECRET to mint a fresh one
#     with a client-credentials grant before every request instead.
# Left unset entirely, curl sends no auth, which only works against a cluster
# with the cluster-admin chain fully permissive.
declare -a REST_AUTH_ARGS=()

# rest_auth_args — recomputes REST_AUTH_ARGS fresh before every request, not
# once at startup, since a static OIDC token was found to expire mid-run. A
# function returning an array does so via this caller-visible global rather
# than a return value, because this bash has no `mapfile` to capture one safely.
rest_auth_args() {
  if [[ -n "${CLUSTER_ADMIN_TOKEN_URL:-}" ]]; then
    local token
    token=$(curl -sS -m 15 -X POST "$CLUSTER_ADMIN_TOKEN_URL" \
      -d "grant_type=client_credentials" \
      -d "client_id=$CLUSTER_ADMIN_CLIENT_ID" \
      -d "client_secret=$CLUSTER_ADMIN_CLIENT_SECRET" \
      ${CLUSTER_ADMIN_AUDIENCE:+-d "audience=$CLUSTER_ADMIN_AUDIENCE"} 2>/dev/null \
      | jq -r '.access_token // empty')
    if [[ -n "$token" ]]; then
      REST_AUTH_ARGS=(-H "Authorization: Bearer $token")
      return 0
    fi
    warn "  could not mint a cluster-admin token from CLUSTER_ADMIN_TOKEN_URL"
  fi
  if [[ -n "${CLUSTER_ADMIN_BEARER_TOKEN:-}" ]]; then
    REST_AUTH_ARGS=(-H "Authorization: Bearer $CLUSTER_ADMIN_BEARER_TOKEN")
  elif [[ -n "${CLUSTER_ADMIN_USER:-}" ]]; then
    REST_AUTH_ARGS=(-u "$CLUSTER_ADMIN_USER:${CLUSTER_ADMIN_PASSWORD:-}")
  else
    REST_AUTH_ARGS=()
  fi
}

rest_url() {
  local broker=$1
  echo "http://localhost:$(pf_local_port_rest "$broker")/cluster/v2/rebalance"
}

# reb_get [broker] — the rebalance status as the given broker reports it. Any
# broker forwards to whichever member coordinates, so broker 0 is the default
# and another is passed only to prove the forwarding.
reb_get() {
  local broker=${1:-0}
  rest_auth_args
  curl -sS -m 20 ${REST_AUTH_ARGS[@]+"${REST_AUTH_ARGS[@]}"} "$(rest_url "$broker")"
}

# reb_post <json body> [broker] — triggers a rebalance, printing `body\nstatus`
# so a caller can assert on the status code as well as the body.
reb_post() {
  local body=$1 broker=${2:-0}
  rest_auth_args
  curl -sS -m 20 -w '\n%{http_code}' -X POST ${REST_AUTH_ARGS[@]+"${REST_AUTH_ARGS[@]}"} \
    -H 'Content-Type: application/json' -d "$body" \
    "$(rest_url "$broker")"
}

reb_delete() {
  local broker=${1:-0}
  rest_auth_args
  curl -sS -m 20 -w '\n%{http_code}' -X DELETE ${REST_AUTH_ARGS[@]+"${REST_AUTH_ARGS[@]}"} \
    "$(rest_url "$broker")"
}

# reb_finished_at — the last completed rebalance's finish time, or "none" before
# one has finished. There is no lifecycle flag left to poll (the top-level
# `state` is the cluster's live balance, not whether a rebalance is running), so
# a caller snapshots this before triggering a rebalance and reb_await waits for
# it to change instead.
reb_finished_at() {
  jq -r '.lastCompletedRebalance.finishedAt // "none"' <<<"$1"
}

# reb_await <timeout_s> <finished_before> [poll_file] — polls until
# lastCompletedRebalance.finishedAt moves past the value captured before the
# rebalance was triggered. When given a file, every sample is appended to it: a
# whole rebalance can be over inside a second, so the sample count is part of
# the evidence and is reported alongside whatever the samples showed.
reb_await() {
  local timeout=$1 before=${2:-none} poll_file=${3:-} started samples state finished
  started=$(date +%s)
  samples=0
  while :; do
    local body; body=$(reb_get 2>/dev/null || echo '{}')
    state=$(jq -r '.state // "UNKNOWN"' <<<"$body")
    finished=$(reb_finished_at "$body")
    samples=$((samples + 1))
    if [[ -n "$poll_file" ]]; then
      jq -c --arg at "$(date -u +%H:%M:%S.%3N 2>/dev/null || date -u +%H:%M:%S)" \
        '{at: $at, state,
          transferring: [(.partitions // [])[] | select(.state == "TRANSFERRING") | .partitionId],
          counts: (reduce (.partitions // [])[] as $p ({}; .[$p.state] += 1))}' \
        <<<"$body" >> "$poll_file"
    fi
    [[ "$finished" != "none" && "$finished" != "$before" ]] && break
    if (( $(date +%s) - started >= timeout )); then
      warn "  rebalance did not finish within ${timeout}s, cluster state $state"
      return 1
    fi
  done
  log "  rebalance finished after $(( $(date +%s) - started ))s over $samples state samples"
}

# reb_capture <name> — the status body, kept because the coordinator's gauges are
# torn down when a rebalance ends, so this is the only place each partition's
# terminal state survives.
reb_capture() {
  reb_get | jq '.' > "$SCENARIO_DIR/$1.json"
  jq -c '{state, last: (.lastCompletedRebalance | {result,
          resultCounts: (reduce (.partitions // [])[] as $p ({}; .[$p.result // "PENDING"] += 1))})}' \
    "$SCENARIO_DIR/$1.json"
}

# reb_run <name> <body> [timeout_s] — the whole cycle a scenario repeats:
# trigger, follow, capture. Prints the completed rebalance's aggregate result.
reb_run() {
  local name=$1 body=${2:-'{}'} timeout=${3:-300}
  local response status before
  before=$(reb_finished_at "$(reb_get 2>/dev/null || echo '{}')")
  response=$(reb_post "$body")
  status=$(http_status <<<"$response")
  http_body <<<"$response" | jq '.' > "$SCENARIO_DIR/$name-accepted.json"
  log "  POST rebalance $body -> $status"
  if [[ "$status" != "202" ]]; then
    warn "  rebalance was not accepted: $(http_body <<<"$response")"
    return 1
  fi
  reb_await "$timeout" "$before" "$SCENARIO_DIR/$name-poll.jsonl" || true
  reb_capture "$name-final" >/dev/null
  jq -r '.lastCompletedRebalance.result // "NONE"' "$SCENARIO_DIR/$name-final.json"
}

# reb_outcome_states <name> — a count per terminal result from a captured body,
# for asserting on what a rebalance made of its partitions. A partition the
# rebalance never reached (a dry run's plan, or one a cancellation left
# untouched before it could be marked CANCELLED) has no result and is counted
# under PENDING.
reb_outcome_states() {
  jq -r '(.lastCompletedRebalance.partitions // []) | group_by(.result // "PENDING")[]
          | "\(.[0].result // "PENDING")=\(length)"' \
    "$SCENARIO_DIR/$1-final.json" | paste -sd' ' -
}

reb_state_count() {
  jq -r --arg state "$2" \
    '[(.lastCompletedRebalance.partitions // [])[] | select((.result // "PENDING") == $state)] | length' \
    "$SCENARIO_DIR/$1-final.json"
}

# reb_unsuccessful_count <name> — partitions whose result is none of the
# outcomes an aggregate COMPLETED allows (TRANSFERRED, ALREADY_LEADER,
# CANCELLED), for asserting "nothing failed" without naming a specific result.
reb_unsuccessful_count() {
  jq -r '[(.lastCompletedRebalance.partitions // [])[]
          | select(.result != null and (.result | IN("TRANSFERRED", "ALREADY_LEADER", "CANCELLED") | not))] | length' \
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
  # Scoped to the last few minutes, because a label selector also picks up a
  # replica that has already died: an OutOfMemoryError from one the run has
  # since replaced would otherwise keep failing this check forever, and every
  # scenario after it would be reported void while the workload was in fact
  # running.
  oom=$(kubectl logs -n "$NS" -l app=starter --since=3m --tail=300 2>/dev/null \
    | grep -ci "outofmemory" || true)
  if [[ "$oom" -gt 0 ]]; then
    warn "  the starter reported OutOfMemoryError in the last three minutes — the workload is dying"
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

# starter_rate <instances per second> — raises or lowers the load the starter
# asks for, and confirms the load actually followed.
#
# Adding a replica is the wrong instrument: it doubles the rate in one step, and
# a starter asked for more than the cluster completes grows an unbounded
# in-flight queue until its heap dies - which happened here within seven minutes
# of doubling.
#
# The chart sets the rate twice, as LOAD_TESTER_STARTER_RATE and as
# -Dapp.starter.rate inside JDK_JAVA_OPTIONS. Only the environment variable is
# read by the current load tester: setting the system property alone rolled the
# pod out and left it starting instances at the old rate. Both are set, so this
# works whichever the deployed load tester reads, and the rate the cluster
# actually sees is then checked rather than assumed.
starter_rate() {
  local rate=$1 opts
  opts=$(kubectl get deploy starter -n "$NS" \
    -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="JDK_JAVA_OPTIONS")].value}' 2>/dev/null)
  if [[ -n "$opts" && "$opts" == *"-Dapp.starter.rate="* ]]; then
    kubectl set env deployment/starter -n "$NS" \
      JDK_JAVA_OPTIONS="$(sed "s/-Dapp.starter.rate=[0-9]*/-Dapp.starter.rate=$rate/" <<<"$opts")" >/dev/null
  fi
  kubectl set env deployment/starter -n "$NS" LOAD_TESTER_STARTER_RATE="$rate" >/dev/null
  kubectl rollout status deployment/starter -n "$NS" --timeout=300s >/dev/null 2>&1 || \
    warn "  the starter did not roll out within 300s"
  log "  starter rate set to $rate instances per second"

  # A rate the workload does not follow would make the scenario measure the old
  # load under a new name, which is worse than not running it.
  # Checked as a band rather than a floor, so that lowering the rate waits for
  # the load to come down instead of passing immediately on the rate it is
  # leaving behind.
  local started created low high
  low=$(awk -v r="$rate" 'BEGIN { print r * 0.75 }')
  high=$(awk -v r="$rate" 'BEGIN { print r * 1.25 }')
  started=$(date +%s)
  while :; do
    created=$(prom_query 'sum(rate(zeebe_element_instance_events_total{namespace="$NAMESPACE", action="activated", type="PROCESS"}[2m]))' || echo "")
    if [[ -n "$created" ]] && compare_num "$created" gt "$low" && compare_num "$created" lt "$high"; then
      log "  instances are being started at $(printf '%.1f' "$created") per second"
      return 0
    fi
    if (( $(date +%s) - started >= 300 )); then
      warn "  asked for $rate per second but the workload is starting ${created:-no} per second"
      return 1
    fi
    sleep 20
  done
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
