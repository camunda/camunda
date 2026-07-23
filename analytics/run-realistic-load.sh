#!/usr/bin/env bash
#
# Runs the camunda-load-tester "realistic load" benchmark (bank dispute handling) against the
# locally-running engine as plain Java processes — no Helm/Kubernetes. It launches the benchmark's
# own Starter and Worker (io.camunda.zeebe.LoadTesterApplication) rather than a hand-rolled driver,
# so job completion AND the two correlation-message publishes are exactly the benchmark's behaviour:
#
#   * customer_notification                    completes + publishes `dispute_process_receive_documents`
#   * dispute_process_request_proof_from_vendor completes + publishes `dispute_process_refund_approved`
#
# keyed by each instance's `correlationKey` variable (set by the sub-process start-event output
# mappings). Without those publishes, instances park forever at "Receive documents" / vendor
# validation. All other job types (and the job-based "Decide on fraud case" user task, job type
# io.camunda.zeebe:userTask) are completed by a plain worker.
#
# Usage:
#   ./run-realistic-load.sh start [ratePerSec]   # deploy + start instances + all workers
#   ./run-realistic-load.sh stop                 # kill everything started here
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$REPO_ROOT/load-tests/load-tester/target/camunda-load-tester-8.10.0-SNAPSHOT-exec.jar"
RUN_DIR="/tmp/eb-demo/realistic-load"
PID_FILE="$RUN_DIR/pids"

# --- `ocpm` scenario (order-to-cash OCPM showcase) --------------------------------------------
#
# Three extra processes (order-intake, order-fulfillment, order-invoicing, region-shipping — 4
# BPMN files) that live in event-bridge/event-bridge-examples/src/main/resources/ocpm/, NOT in the
# load-tester module. The load-tester's Starter only deploys resources it can find on ITS OWN
# classpath (DeployResourceCommandImpl#addResourceFromClasspath has no file-path fallback), so
# getting these BPMNs deployed without touching load-tester source needs one trick: launch the
# exec jar via Spring Boot's bundled PropertiesLauncher (present in every repackaged Boot jar,
# alongside the default JarLauncher) instead of plain `-jar`, and pass it `-Dloader.path=<dir>` to
# splice an extra directory onto the app's classpath. This is documented Spring Boot behaviour
# (see the top comment of load-tester's application.yaml: "Override any property via environment
# variables"), not a hack — verified locally: a throwaway classpath probe confirmed
# `-Dloader.path=.../ocpm -Dloader.main=io.camunda.zeebe.LoadTesterApplication
# org.springframework.boot.loader.launch.PropertiesLauncher` makes `order-intake.bpmn` resolvable
# via the same `ClassLoader#getResourceAsStream` call DeployResourceCommandImpl uses.
#
# See event-bridge/event-bridge-examples/docs/ocpm-showcase.md for the object model, the ground
# truth this scenario is built to exercise, and a documented gap: the load-tester's Worker
# publishes messages with a correlationKey only (no payload variables — see
# io.camunda.zeebe.worker.Worker#publishMessage), so the `ship-order` message's payload-merge
# ground-truth line item is not produced by this driver as-is.
OCPM_RESOURCES_DIR="$REPO_ROOT/event-bridge/event-bridge-examples/src/main/resources/ocpm"
RUN_DIR_OCPM="/tmp/eb-demo/realistic-load-ocpm"
PID_FILE_OCPM="$RUN_DIR_OCPM/pids"
LOAD_TESTER_MAIN_CLASS="io.camunda.zeebe.LoadTesterApplication"

# Launches the load-tester exec jar with the ocpm BPMN/payload directory spliced onto its
# classpath (see comment block above). Extra args are forwarded verbatim (e.g.
# --spring.profiles.active=starter --server.port=0).
run_ocpm_jar() {
  # `exec` here (not at the call site) so the java process replaces this function's subshell —
  # `exec` cannot be applied to a shell function directly, only to a real command/builtin.
  exec java -Xmx256m \
    -Dloader.path="$OCPM_RESOURCES_DIR" \
    -Dloader.main="$LOAD_TESTER_MAIN_CLASS" \
    -cp "$JAR" org.springframework.boot.loader.launch.PropertiesLauncher "$@"
}

# Engine (StandaloneCamunda / OC): gRPC 26500, REST 8088, auth disabled.
export ZEEBE_GRPC_ADDRESS="${ZEEBE_GRPC_ADDRESS:-http://localhost:26500}"
export ZEEBE_REST_ADDRESS="${ZEEBE_REST_ADDRESS:-http://localhost:8088}"
export ZEEBE_AUTH_METHOD="${ZEEBE_AUTH_METHOD:-none}"

# Mirrors the benchmark's values-realistic-benchmark.yaml: one worker per job type. Format is
# "type|message|threads": message (optional) is published on completion keyed by the instance's
# correlationKey (the benchmark's sendMessage workers); threads (optional) overrides the execution-
# thread count. The benchmark gives the two hot types — the multi-instance vendor-proof task and
# refunding — 30 threads and leaves the rest at the default; that (not more pods) is how it drains
# the fan-out. We add two workers the benchmark's realistic set omits but this model needs so
# instances don't park: inform_about_failed_claim (unsuccessful-claim send task) and
# io.camunda.zeebe:userTask (the job-based "Decide on fraud case" user task).
WORKERS=(
  "customer_notification|dispute_process_receive_documents|4"
  "dispute_process_request_proof_from_vendor|dispute_process_refund_approved|30"
  "dispute_process_request_get_vendor_info||60"
  "extract_data_from_document||4"
  "inform_about_failed_claim||"
  "inform_about_successful_claim||"
  "refunding||30"
  "io.camunda.zeebe:userTask||"
)

start() {
  local rate="${1:-1}"
  [ -f "$JAR" ] || { echo "Missing $JAR — build it: ./mvnw install -pl load-tests/load-tester -am -Dquickly -T1C"; exit 1; }
  mkdir -p "$RUN_DIR"
  : > "$PID_FILE"

  # Benchmark-faithful defaults: 300 ms completion delay, 30 in-flight (application.yaml), one
  # instance per job type. The hot types carry threads=30 via the WORKERS table above; the rest use
  # the default. WORKER_REPLICAS (default 1) can still fan a type out to more instances if needed.
  local replicas="${WORKER_REPLICAS:-1}"

  echo "Launching ${#WORKERS[@]} job types (replicas=${replicas}) (engine=$ZEEBE_REST_ADDRESS)…"
  for spec in "${WORKERS[@]}"; do
    local type msg threads
    IFS='|' read -r type msg threads <<< "$spec"
    local safe="${type//[:.]/_}"
    for r in $(seq 1 "$replicas"); do
      (
        export LOAD_TESTER_WORKER_JOB_TYPE="$type"
        export LOAD_TESTER_WORKER_WORKER_NAME="realistic-$safe-$r"
        [ -n "$threads" ] && export LOAD_TESTER_WORKER_THREADS="$threads"
        # Complete jobs WITH variables — the benchmark default big_payload (generic, non-domain keys),
        # so completions enrich instance variables (the analytics pipeline sees real payloads) WITHOUT
        # clobbering the process's control variables: its keys don't clash with customerId /
        # disputeDetails.disputePositions (the multi-instance collection) or correlationKey.
        export LOAD_TESTER_WORKER_PAYLOAD_PATH="bpmn/big_payload.json"
        export LOAD_TESTER_MONITOR_DATA_AVAILABILITY=false
        if [ -n "$msg" ]; then
          export LOAD_TESTER_WORKER_SEND_MESSAGE=true
          export LOAD_TESTER_WORKER_MESSAGE_NAME="$msg"
          export LOAD_TESTER_WORKER_CORRELATION_KEY_VARIABLE_NAME="correlationKey"
        fi
        exec java -Xmx256m -jar "$JAR" \
          --spring.profiles.active=worker --server.port=0
      ) > "$RUN_DIR/worker-$safe-$r.log" 2>&1 &
      echo "$!" >> "$PID_FILE"
    done
    echo "  worker $type × $replicas${msg:+  (publishes $msg)}"
  done

  launch_starter "$rate"

  echo
  echo "All up. Logs: $RUN_DIR/*.log   Stop with: $0 stop"
}

# Launch the instance starter. Rate = $1 instances per STARTER_RATE_DURATION (default 1s), so
# `launch_starter 1` with STARTER_RATE_DURATION=3s starts one instance every 3 seconds. Appends its
# pid to PID_FILE so `stop` tears it down with everything else.
launch_starter() {
  local rate="${1:-1}"
  local rate_duration="${STARTER_RATE_DURATION:-1s}"
  echo "Launching starter (deploy + $rate PI / $rate_duration)…"
  (
    export LOAD_TESTER_STARTER_PROCESS_ID="bankDisputeHandling"
    export LOAD_TESTER_STARTER_BPMN_XML_PATH="bpmn/realistic/bankCustomerComplaintDisputeHandling.bpmn"
    export LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_0_="bpmn/realistic/refundingProcess.bpmn"
    export LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_1_="bpmn/realistic/determineFraudRatingConfidence.dmn"
    export LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_2_="bpmn/realistic/decide_on_fraud_case.form"
    export LOAD_TESTER_STARTER_PAYLOAD_PATH="bpmn/realistic/realisticPayload.json"
    export LOAD_TESTER_STARTER_RATE="$rate"
    export LOAD_TESTER_STARTER_RATE_DURATION="$rate_duration"
    export LOAD_TESTER_STARTER_THREADS=1
    export LOAD_TESTER_MONITOR_DATA_AVAILABILITY=false
    exec java -Xmx256m -jar "$JAR" \
      --spring.profiles.active=starter --server.port=0
  ) > "$RUN_DIR/starter.log" 2>&1 &
  echo "$!" >> "$PID_FILE"
  echo "  starter pid=$!"
}

stop() {
  local found=false
  for pid_file in "$PID_FILE" "$PID_FILE_OCPM"; do
    [ -f "$pid_file" ] || continue
    found=true
    while read -r pid; do
      [ -n "$pid" ] && kill "$pid" 2>/dev/null && echo "killed $pid" || true
    done < "$pid_file"
    rm -f "$pid_file"
  done
  "$found" || echo "Nothing to stop ($PID_FILE / $PID_FILE_OCPM not found)."
}

# (Re)launch only the starter — kills any running starter first, leaves the workers alone. Use to
# retune the creation rate on the fly, e.g. STARTER_RATE_DURATION=3s ./run-realistic-load.sh starter 1
restart_starter() {
  mkdir -p "$RUN_DIR"; touch "$PID_FILE"
  pkill -f "spring.profiles.active=starter" 2>/dev/null && echo "stopped previous starter" || true
  launch_starter "${1:-1}"
}

# Job types for the `ocpm` scenario. Format: "type|message|correlationKeyVar|threads|delay|timeout"
#   message/correlationKeyVar (optional): published on completion, keyed by that job variable —
#     same sendMessage mechanism the default scenario uses for its two dispute messages.
#   threads (optional): overrides camunda.client.execution-threads for this worker process.
#   delay (optional): overrides the fixed per-job completion delay (default 300ms). The
#     load-tester's Worker sleeps this exact duration every time — no jitter/range support — so
#     this is a fixed stand-in for the spec'd ranges (see docs/ocpm-showcase.md, "Approximations").
#   timeout (optional): overrides camunda.client.worker.defaults.timeout (default 1800ms) for this
#     worker process only. Needed for order_intake_manual_credit_review: its delay (5s) exceeds the
#     default job-activation timeout, which would otherwise let the broker hand the job to another
#     worker mid-sleep. This works because Spring env vars always outrank application.yaml values,
#     even ones application.yaml sets to a literal (not `${VAR:default}`) — no load-tester source
#     change needed, see the CAMUNDA_CLIENT_WORKER_DEFAULTS_TIMEOUT export below.
WORKERS_OCPM=(
  "order_intake_manual_credit_review|||2|5000ms|30s"
  "order_intake_auto_credit_check||||200ms"
  "order_fulfillment_pick_stock||||300ms"
  "order_fulfillment_pack_order||||300ms"
  "order_fulfillment_publish_ship_order|ship-order|region|4|300ms"
  "order_invoicing_create_invoice||||300ms"
  "region_shipping_load_parcel||||150ms"
)

# EU/US/APAC/LATAM, one long-running region-shipping instance each (see docs/ocpm-showcase.md for
# why a slow trickle rate is the closest fit to "restart on complete" — the Starter is timer-
# scheduled, not completion-triggered).
OCPM_REGIONS=(eu us apac latam)
OCPM_REGION_STARTER_INTERVAL="${OCPM_REGION_STARTER_INTERVAL:-90s}"

start_ocpm() {
  local rate="${1:-1}"
  [ -f "$JAR" ] || { echo "Missing $JAR — build it: ./mvnw install -pl load-tests/load-tester -am -Dquickly -T1C"; exit 1; }
  [ -d "$OCPM_RESOURCES_DIR" ] || { echo "Missing $OCPM_RESOURCES_DIR"; exit 1; }
  mkdir -p "$RUN_DIR_OCPM"
  : > "$PID_FILE_OCPM"

  echo "Launching ${#WORKERS_OCPM[@]} ocpm job types (engine=$ZEEBE_REST_ADDRESS)…"
  for spec in "${WORKERS_OCPM[@]}"; do
    local type msg corrVar threads delay timeout
    IFS='|' read -r type msg corrVar threads delay timeout <<< "$spec"
    local safe="${type//[:.]/_}"
    (
      export LOAD_TESTER_WORKER_JOB_TYPE="$type"
      export LOAD_TESTER_WORKER_WORKER_NAME="ocpm-$safe"
      [ -n "$threads" ] && export LOAD_TESTER_WORKER_THREADS="$threads"
      [ -n "$delay" ] && export LOAD_TESTER_WORKER_COMPLETION_DELAY="$delay"
      [ -n "$timeout" ] && export CAMUNDA_CLIENT_WORKER_DEFAULTS_TIMEOUT="$timeout"
      export LOAD_TESTER_MONITOR_DATA_AVAILABILITY=false
      if [ -n "$msg" ]; then
        export LOAD_TESTER_WORKER_SEND_MESSAGE=true
        export LOAD_TESTER_WORKER_MESSAGE_NAME="$msg"
        export LOAD_TESTER_WORKER_CORRELATION_KEY_VARIABLE_NAME="$corrVar"
      fi
      run_ocpm_jar --spring.profiles.active=worker --server.port=0
    ) > "$RUN_DIR_OCPM/worker-$safe.log" 2>&1 &
    echo "$!" >> "$PID_FILE_OCPM"
    echo "  worker $type${msg:+  (publishes $msg keyed by $corrVar)}"
  done

  launch_ocpm_intake_starter "$rate"
  for region in "${OCPM_REGIONS[@]}"; do
    launch_ocpm_region_starter "$region"
  done

  echo
  echo "ocpm scenario up. Logs: $RUN_DIR_OCPM/*.log   Stop with: $0 stop"
}

# order-intake starter: deploys all 3 call-chain BPMNs (order-intake, order-fulfillment,
# order-invoicing — region-shipping is deployed by its own starters below). `seq` is the
# load-tester's native auto-incrementing per-instance counter (LOAD_TESTER_STARTER_BUSINESS_KEY),
# from which order-intake's start event FEEL-derives every other root variable — see
# docs/ocpm-showcase.md, "Payload generation", for why no payload-pool file was needed.
launch_ocpm_intake_starter() {
  local rate="${1:-1}"
  local rate_duration="${STARTER_RATE_DURATION:-1s}"
  echo "Launching order-intake starter ($rate PI / $rate_duration)…"
  (
    export LOAD_TESTER_STARTER_PROCESS_ID="order-intake"
    export LOAD_TESTER_STARTER_BPMN_XML_PATH="order-intake.bpmn"
    export LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_0_="order-fulfillment.bpmn"
    export LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_1_="order-invoicing.bpmn"
    export LOAD_TESTER_STARTER_PAYLOAD_PATH="order-intake-seed.json"
    export LOAD_TESTER_STARTER_BUSINESS_KEY="seq"
    export LOAD_TESTER_STARTER_RATE="$rate"
    export LOAD_TESTER_STARTER_RATE_DURATION="$rate_duration"
    export LOAD_TESTER_STARTER_THREADS=1
    export LOAD_TESTER_MONITOR_DATA_AVAILABILITY=false
    run_ocpm_jar --spring.profiles.active=starter --server.port=0
  ) > "$RUN_DIR_OCPM/starter-order-intake.log" 2>&1 &
  echo "$!" >> "$PID_FILE_OCPM"
  echo "  order-intake starter pid=$!"
}

# One starter per region, each deploying region-shipping.bpmn and seeding {region: "EU"|...}. Rate
# is a slow trickle (see OCPM_REGIONS / OCPM_REGION_STARTER_INTERVAL comment above).
launch_ocpm_region_starter() {
  local region="$1"
  # bash 3.2 (macOS's default /bin/bash) has no ${var^^} case conversion — use tr instead.
  local region_upper
  region_upper="$(echo "$region" | tr '[:lower:]' '[:upper:]')"
  echo "Launching region-shipping starter for $region_upper (1 PI / $OCPM_REGION_STARTER_INTERVAL)…"
  (
    export LOAD_TESTER_STARTER_PROCESS_ID="region-shipping"
    export LOAD_TESTER_STARTER_BPMN_XML_PATH="region-shipping.bpmn"
    export LOAD_TESTER_STARTER_PAYLOAD_PATH="region-shipping-$region.json"
    export LOAD_TESTER_STARTER_RATE=1
    export LOAD_TESTER_STARTER_RATE_DURATION="$OCPM_REGION_STARTER_INTERVAL"
    export LOAD_TESTER_STARTER_THREADS=1
    export LOAD_TESTER_MONITOR_DATA_AVAILABILITY=false
    run_ocpm_jar --spring.profiles.active=starter --server.port=0
  ) > "$RUN_DIR_OCPM/starter-region-$region.log" 2>&1 &
  echo "$!" >> "$PID_FILE_OCPM"
  echo "  region-shipping/$region starter pid=$!"
}

case "${1:-}" in
  start) shift; start "$@" ;;
  starter) shift; restart_starter "$@" ;;
  ocpm) shift; start_ocpm "$@" ;;
  stop)  stop ;;
  *) echo "Usage: $0 {start [ratePerSec] | starter [rate] (STARTER_RATE_DURATION=Ns) | ocpm [ratePerSec] | stop}"; exit 1 ;;
esac
