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
  "customer_notification|dispute_process_receive_documents|"
  "dispute_process_request_proof_from_vendor|dispute_process_refund_approved|30"
  "dispute_process_request_get_vendor_info||"
  "extract_data_from_document||"
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
  [ -f "$PID_FILE" ] || { echo "Nothing to stop ($PID_FILE not found)."; exit 0; }
  while read -r pid; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null && echo "killed $pid" || true
  done < "$PID_FILE"
  rm -f "$PID_FILE"
}

# (Re)launch only the starter — kills any running starter first, leaves the workers alone. Use to
# retune the creation rate on the fly, e.g. STARTER_RATE_DURATION=3s ./run-realistic-load.sh starter 1
restart_starter() {
  mkdir -p "$RUN_DIR"; touch "$PID_FILE"
  pkill -f "spring.profiles.active=starter" 2>/dev/null && echo "stopped previous starter" || true
  launch_starter "${1:-1}"
}

case "${1:-}" in
  start) shift; start "$@" ;;
  starter) shift; restart_starter "$@" ;;
  stop)  stop ;;
  *) echo "Usage: $0 {start [ratePerSec] | starter [rate] (STARTER_RATE_DURATION=Ns) | stop}"; exit 1 ;;
esac
