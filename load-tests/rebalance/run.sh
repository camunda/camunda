#!/usr/bin/env bash
# Drives a sustained, varied run of the coordinated leadership rebalance against
# a load-test cluster, asserting on metrics as it goes.
#
# Run `./run.sh --help` for usage. See README.md for what it needs and what it
# leaves behind.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- Defaults --------------------------------------------------------------

NS=""
BROKERS=6
PARTITIONS=12
OUT_ROOT="$HOME/Documents/clt-test-plan/runs"
ZBCHAOS="${ZBCHAOS:-zbchaos}"
TIME_SCALE=1
ASSERT_TIMEOUT=120
SCRAPE_SETTLE=25
STRICT=false
ONLY=""
SKIP=""
FROM=""
PF_BASE_PORT=9600
PROM_NAMESPACE=monitoring
PROM_SERVICE=kube-prometheus-stack-prometheus
PROM_PORT=9090
NETEM_DELAY=200ms
NETEM_LOSS=2%
NETEM_IMAGE=nicolaka/netshoot:latest
BROKER_CONTAINER=orchestration
SOAK_ITERATIONS=12
BASELINE_PI_PER_SECOND=50
GRAFANA_URL=https://dashboard.benchmark.camunda.cloud
# The shared Grafana serves whatever version of the Zeebe dashboard it was
# provisioned with, which will not carry the branch's Rebalancing row. Point
# these at a privately imported copy of monitor/grafana/zeebe.json to get links
# that land on the row this suite exercises.
DASHBOARD_UID=zeebe-dashboard
DASHBOARD_SLUG=zeebe
LAGGING_BROKER=""

usage() {
  cat <<'EOF'
Usage: run.sh --namespace <ns> [options]

Drives the rebalance scenarios in order against a running load-test cluster,
asserting on Prometheus after each one so that a scenario whose metrics never
materialised is reported during the run rather than discovered afterwards.

Options:
  -n, --namespace <ns>    Load-test namespace, e.g. c8-mk-clt-sustained-20260803 (required)
      --brokers <n>       Brokers in the cluster (default 6)
      --partitions <n>    Partitions in the cluster (default 12)
      --out <dir>         Where to keep the run's evidence (default ~/Documents/clt-test-plan/runs)
      --zbchaos <path>    zbchaos binary, used for the leadership checks (default `zbchaos` on PATH)
      --time-scale <f>    Multiply every wait by this, e.g. 0.05 for a quick shakedown (default 1)
      --assert-timeout <s> How long an assertion retries while metrics scrape (default 120)
      --scrape-settle <s> Pause between scenarios so their counters do not overlap (default 25)
      --soak-iterations <n> Rebalances in the soak scenario (default 12, one every 5 min)
      --netem-image <ref> Image with tc, for injecting replication lag (default nicolaka/netshoot:latest)
      --broker-container <name> Broker container name in the pod (default orchestration)
      --grafana-url <url> Grafana base URL for the summary's links (default the benchmark Grafana)
      --dashboard-uid <uid> Dashboard uid to link to; use your imported copy's uid if the shared
                          dashboard predates the Rebalancing row (default zeebe-dashboard)
      --only <a,b>        Run only these scenarios
      --skip <a,b>        Skip these scenarios
      --from <scenario>   Start at this scenario, skipping the ones before it
      --strict            Stop at the first scenario with a failed assertion
      --list              List the scenarios and exit
  -h, --help              Show this help

Preflight refuses to start unless the namespace is reachable, the workload is
alive, the leader-balancer CronJob is suspended, and every metric the scenarios
assert on is being scraped.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--namespace) NS=$2; shift 2 ;;
    --brokers) BROKERS=$2; shift 2 ;;
    --partitions) PARTITIONS=$2; shift 2 ;;
    --out) OUT_ROOT=$2; shift 2 ;;
    --zbchaos) ZBCHAOS=$2; shift 2 ;;
    --time-scale) TIME_SCALE=$2; shift 2 ;;
    --assert-timeout) ASSERT_TIMEOUT=$2; shift 2 ;;
    --scrape-settle) SCRAPE_SETTLE=$2; shift 2 ;;
    --soak-iterations) SOAK_ITERATIONS=$2; shift 2 ;;
    --netem-image) NETEM_IMAGE=$2; shift 2 ;;
    --broker-container) BROKER_CONTAINER=$2; shift 2 ;;
    --grafana-url) GRAFANA_URL=$2; shift 2 ;;
    --dashboard-uid) DASHBOARD_UID=$2; shift 2 ;;
    --only) ONLY=$2; shift 2 ;;
    --skip) SKIP=$2; shift 2 ;;
    --from) FROM=$2; shift 2 ;;
    --strict) STRICT=true; shift ;;
    --list) LIST_ONLY=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

PROM_URL="http://localhost:$PROM_PORT"

# --- Wiring ----------------------------------------------------------------

# shellcheck source=lib/common.sh
source "$HERE/lib/common.sh"
# shellcheck source=lib/prom.sh
source "$HERE/lib/prom.sh"
# shellcheck source=lib/cluster.sh
source "$HERE/lib/cluster.sh"
# shellcheck source=scenarios.sh
source "$HERE/scenarios.sh"

if [[ "${LIST_ONLY:-false}" == "true" ]]; then
  printf '%s\n' "${SCENARIOS[@]}"
  exit 0
fi

[[ -z "$NS" ]] && { echo "--namespace is required" >&2; usage >&2; exit 1; }

for tool in kubectl jq curl awk python3; do
  command -v "$tool" >/dev/null || { echo "$tool is not on PATH" >&2; exit 1; }
done
command -v "$ZBCHAOS" >/dev/null || [[ -x "$ZBCHAOS" ]] || {
  echo "zbchaos not found at '$ZBCHAOS'; pass --zbchaos <path>" >&2; exit 1
}

RUN_START_MS=$(python3 -c 'import time; print(int(time.time() * 1000))')
RUN_DIR="$OUT_ROOT/$NS-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RUN_DIR"
: > "$RUN_DIR/timeline.jsonl"
RUN_PASSED=0
RUN_FAILED=0
SCENARIO_INDEX=0
SCENARIO_DIR="$RUN_DIR"

cleanup() {
  # Injected lag outlives the run if it is not cleared, and would quietly spoil
  # whatever is measured next on this cluster.
  clear_lag || true
  pf_stop_all || true
}
trap cleanup EXIT

# --- Preflight -------------------------------------------------------------

preflight() {
  section "preflight"
  SCENARIO_DIR="$RUN_DIR/00-preflight"
  mkdir -p "$SCENARIO_DIR"
  : > "$SCENARIO_DIR/assertions.tsv"

  kubectl get ns "$NS" >/dev/null 2>&1 || die "namespace $NS does not exist"
  pf_start_all || die "could not open the port forwards this run reads everything through"
  # Checked on its own, so that an unreachable Prometheus is reported as one
  # rather than as every metric it would have answered for reading as absent.
  curl -sS -m 10 -o /dev/null "$PROM_URL/-/healthy" 2>/dev/null || \
    die "Prometheus is not answering on $PROM_URL — check the forward to $PROM_NAMESPACE/$PROM_SERVICE"

  local ready
  ready=$(kubectl get pods -n "$NS" -l app.kubernetes.io/component=zeebe-broker \
    --no-headers 2>/dev/null | awk '$2 == "1/1" && $3 == "Running"' | wc -l | tr -d ' ')
  [[ "$ready" == "$BROKERS" ]] || die "$ready of $BROKERS brokers are ready; wait for the cluster before starting"

  local active; active=$(healthy_partitions)
  [[ "$active" == "$((PARTITIONS * 3))" ]] || \
    warn "the configuration reports $active active partition replicas, expected $((PARTITIONS * 3)) for $PARTITIONS partitions at replication factor 3"

  # The chart's leader-balancer CronJob POSTs the legacy endpoint every ten
  # minutes. Left running it moves leadership underneath every measurement here.
  local suspended
  suspended=$(kubectl get cronjob leader-balancer -n "$NS" -o jsonpath='{.spec.suspend}' 2>/dev/null || echo "absent")
  [[ "$suspended" == "true" || "$suspended" == "absent" ]] || \
    die "the leader-balancer CronJob is running; suspend it first: kubectl patch cronjob leader-balancer -n $NS -p '{\"spec\":{\"suspend\":true}}'"

  workload_alive || die "the workload is not alive, so nothing measured would mean anything"

  local status
  status=$(reb_get | jq -r '.status // "unreachable"')
  [[ "$status" == "IDLE" ]] || die "the rebalance endpoint reports $status, expected IDLE"

  # Every metric the scenarios asserts on is checked here, because a run whose
  # panels stay empty proves nothing and three hours is too long to find out.
  local query
  for query in \
      "balance:$BALANCE_QUERY" \
      "throughput:$PI_PER_SECOND_QUERY" \
      "flow control:sum(zeebe_flow_control_total{namespace=\"\$NAMESPACE\"})" \
      "replication lag:count(zeebe_raft_replication_lag_bytes{namespace=\"\$NAMESPACE\"})" \
      "jvm heap:max(jvm_memory_used_bytes{namespace=\"\$NAMESPACE\", area=\"heap\"})"; do
    local label=${query%%:*} promql=${query#*:} value
    value=$(prom_query "$promql" || echo "")
    [[ -n "$value" ]] || die "Prometheus has no data for $label — check the ServiceMonitor and the namespace before starting"
    log "  scraped $label: $value"
  done

  # The rebalance metrics only exist once a rebalance has run, so preflight runs
  # one rather than assuming they will appear later.
  log "  running a rebalance so that the rebalance metrics can be checked"
  reb_run "preflight" '{}' 120 >/dev/null
  settle 60 "a scrape to cover the preflight rebalance"
  assert_num "rebalances are counted in Prometheus" \
    'sum(zeebe_cluster_rebalance_elapsed_seconds_count{namespace="$NAMESPACE"})' ge 1
  assert_num "the balance gauge is published by every member" \
    'count(zeebe_cluster_partition_balanced{namespace="$NAMESPACE"})' eq "$((PARTITIONS * BROKERS))"

  if grep -q '^FAIL' "$SCENARIO_DIR/assertions.tsv"; then
    die "preflight assertions failed; fix the observability path before spending hours on a run"
  fi
  log "preflight passed — the metrics the run asserts on are all being scraped"
}

# --- Run -------------------------------------------------------------------

wanted() {
  local scenario=$1
  [[ -n "$ONLY" ]] && { [[ ",$ONLY," == *",$scenario,"* ]] || return 1; }
  [[ -n "$SKIP" && ",$SKIP," == *",$scenario,"* ]] && return 1
  return 0
}

main() {
  log "run directory: $RUN_DIR"
  log "namespace $NS, $BROKERS brokers, $PARTITIONS partitions, time scale $TIME_SCALE"

  preflight

  local -a selected=()
  local started=false scenario
  for scenario in "${SCENARIOS[@]}"; do
    if [[ -n "$FROM" && "$started" == "false" ]]; then
      [[ "$scenario" == "$FROM" ]] && started=true || continue
    fi
    wanted "$scenario" && selected+=("$scenario")
  done
  SCENARIO_TOTAL=${#selected[@]}
  [[ "$SCENARIO_TOTAL" -eq 0 ]] && die "no scenarios selected"
  log "running ${SCENARIO_TOTAL} scenarios: ${selected[*]}"

  for scenario in "${selected[@]}"; do
    SCENARIO_INDEX=$((SCENARIO_INDEX + 1))
    scenario_begin "$scenario"
    pf_check
    settle_scrape "the previous scenario's metrics to land before this one takes its baselines"
    workload_alive || warn "the workload is not alive at the start of $scenario"
    # A scenario that fails part-way still records what it got, and the run goes
    # on: one broken scenario is not a reason to lose the ten after it.
    "scenario_$scenario" || warn "scenario $scenario exited non-zero"
    clear_lag || true
    scenario_end
  done

  # Whether the dashboard tells the story is the point of the run, so it is
  # checked rather than assumed: every query behind the Rebalancing row, over the
  # window the run covered.
  section "checking the dashboard row against the run's window"
  local minutes; minutes=$(( ($(now_ms) - RUN_START_MS) / 60000 + 1 ))
  "$HERE/check-panels.sh" --namespace "$NS" --prom "$PROM_URL" --range "${minutes}m" \
    --rate-interval 5m | tee "$RUN_DIR/dashboard-panels.txt" | tee -a "$RUN_DIR/run.log" >&2 || \
    warn "some dashboard panels would draw nothing for this run — see dashboard-panels.txt"

  render_summary
  section "done: $RUN_PASSED assertions passed, $RUN_FAILED failed"
  [[ "$RUN_FAILED" -eq 0 ]]
}

main "$@"
