#!/usr/bin/env bash
# run-local.sh — Start the realistic load test locally (Starter + 7 Workers).
#
# Usage:
#   ./load-tests/run-local.sh            # build then run
#   ./load-tests/run-local.sh --no-build # skip build (already built)
#   ./load-tests/run-local.sh --stop     # kill all running instances
#
# Prometheus metrics ports (19600-range avoids conflict with docker-compose):
#   Starter                                          → 19600  (9600 is taken by Zeebe orchestration)
#   Worker: refunding                                → 19601
#   Worker: customer_notification                    → 19602
#   Worker: extract_data_from_document               → 19603
#   Worker: inform_about_failed_claim                → 19604
#   Worker: inform_about_successful_claim            → 19605
#   Worker: dispute_process_request_proof_from_vendor→ 19606
#   Worker: dispute_process_request_get_vendor_info  → 19607

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MODULE_DIR="${REPO_ROOT}/load-tests/load-tester"
CONFIG_FILE="${MODULE_DIR}/src/main/resources/application-local.conf"
NOTIFY_CONFIG_FILE="${MODULE_DIR}/src/main/resources/application-local-notify.conf"
LOG_DIR="${SCRIPT_DIR}/logs"
PID_FILE="${SCRIPT_DIR}/.load-test.pids"
JAVA="$(which java)"

# ── Helpers ────────────────────────────────────────────────────────────────────

log() { echo "[$(date '+%H:%M:%S')] $*"; }

stop_all() {
  if [[ ! -f "${PID_FILE}" ]]; then
    log "No PID file found — nothing to stop."
    return
  fi
  log "Stopping all load test processes..."
  while IFS= read -r pid; do
    if kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" && log "  Killed PID ${pid}"
    fi
  done < "${PID_FILE}"
  rm -f "${PID_FILE}"
  log "Done."
}

# ── Stop mode ──────────────────────────────────────────────────────────────────

if [[ "${1:-}" == "--stop" ]]; then
  stop_all
  exit 0
fi

# ── Cleanup on Ctrl+C ─────────────────────────────────────────────────────────

trap 'log "Caught signal — stopping all processes..."; stop_all; exit 0' INT TERM

# ── Build ──────────────────────────────────────────────────────────────────────

cd "${REPO_ROOT}"

if [[ "${1:-}" != "--no-build" ]]; then
  log "Building load-tester module..."
  ./mvnw -am -pl load-tests/load-tester package -DskipTests -DskipChecks -q
  log "Build complete."
fi

# Build the classpath once — avoids Maven overhead on every java invocation
log "Resolving classpath..."
./mvnw -pl load-tests/load-tester dependency:build-classpath \
  -Dmdep.outputFile="${MODULE_DIR}/target/classpath.txt" -q

JAR="$(ls "${MODULE_DIR}"/target/camunda-load-tester-*.jar | grep -v sources | head -1)"
CLASSPATH="${JAR}:$(cat "${MODULE_DIR}/target/classpath.txt")"
log "Classpath ready (JAR: $(basename "${JAR}"))"

# ── Setup ──────────────────────────────────────────────────────────────────────

mkdir -p "${LOG_DIR}"
rm -f "${PID_FILE}"

# ── Launch function ────────────────────────────────────────────────────────────

# start_process <label> <main-class> <monitoring-port> [KEY=VALUE env overrides...]
# Optional: set PROCESS_CONFIG_FILE before calling to override the config file.
start_process() {
  local label="$1"
  local main_class="$2"
  local port="$3"
  shift 3
  local log_file="${LOG_DIR}/${label}.log"
  local cfg="${PROCESS_CONFIG_FILE:-${CONFIG_FILE}}"

  log "Starting ${label} on metrics port ${port} → ${log_file}"

  # Run java directly (not via Maven) so the process is fully independent and
  # long-lived background threads (job workers, gRPC, HTTP server) are not
  # killed when Maven exits.
  env "$@" MONITORING_PORT="${port}" \
    "${JAVA}" \
      -cp "${CLASSPATH}" \
      -Dconfig.file="${cfg}" \
      "${main_class}" \
    > "${log_file}" 2>&1 &

  local pid=$!
  echo "${pid}" >> "${PID_FILE}"
  log "  PID ${pid}"
  unset PROCESS_CONFIG_FILE
}

# ── Start Starter ──────────────────────────────────────────────────────────────

start_process "starter" "io.camunda.zeebe.Starter" 19600

# Small pause so the Starter deploys the BPMNs before workers start polling
sleep 5

# ── Start Workers ──────────────────────────────────────────────────────────────

start_process "worker-refunding"              "io.camunda.zeebe.Worker" 19601 \
  ZEEBE_WORKER_JOB_TYPE=refunding \
  ZEEBE_WORKER_NAME=worker-refunding

# Uses notify config: completes the job AND publishes the correlation message so
# the "Receive documents" receive task is unblocked (TTL=30s so the message is
# buffered until the subscription is created a few ms later).
PROCESS_CONFIG_FILE="${NOTIFY_CONFIG_FILE}" \
start_process "worker-customer-notification"  "io.camunda.zeebe.Worker" 19602 \
  ZEEBE_WORKER_JOB_TYPE=customer_notification \
  ZEEBE_WORKER_NAME=worker-customer-notification

start_process "worker-extract-data"           "io.camunda.zeebe.Worker" 19603 \
  ZEEBE_WORKER_JOB_TYPE=extract_data_from_document \
  ZEEBE_WORKER_NAME=worker-extract-data

start_process "worker-inform-failed"          "io.camunda.zeebe.Worker" 19604 \
  ZEEBE_WORKER_JOB_TYPE=inform_about_failed_claim \
  ZEEBE_WORKER_NAME=worker-inform-failed

start_process "worker-inform-success"         "io.camunda.zeebe.Worker" 19605 \
  ZEEBE_WORKER_JOB_TYPE=inform_about_successful_claim \
  ZEEBE_WORKER_NAME=worker-inform-success

start_process "worker-proof-from-vendor"      "io.camunda.zeebe.Worker" 19606 \
  ZEEBE_WORKER_JOB_TYPE=dispute_process_request_proof_from_vendor \
  ZEEBE_WORKER_NAME=worker-proof-from-vendor

start_process "worker-vendor-info"            "io.camunda.zeebe.Worker" 19607 \
  ZEEBE_WORKER_JOB_TYPE=dispute_process_request_get_vendor_info \
  ZEEBE_WORKER_NAME=worker-vendor-info

# ── Summary ────────────────────────────────────────────────────────────────────

log ""
log "All processes started. Logs in: ${LOG_DIR}/"
log ""
log "Metrics endpoints:"
log "  Starter                              → http://localhost:19600/metrics"
log "  Worker: refunding                    → http://localhost:19601/metrics"
log "  Worker: customer_notification        → http://localhost:19602/metrics"
log "  Worker: extract_data_from_document   → http://localhost:19603/metrics"
log "  Worker: inform_about_failed_claim    → http://localhost:19604/metrics"
log "  Worker: inform_about_successful_claim→ http://localhost:19605/metrics"
log "  Worker: proof_from_vendor            → http://localhost:19606/metrics"
log "  Worker: vendor_info                  → http://localhost:19607/metrics"
log ""
log "Prometheus: http://localhost:9090"
log ""
log "Tail logs:  tail -f ${LOG_DIR}/starter.log"
log "Stop all:   $0 --stop"
log ""
log "Waiting for processes (Ctrl+C to stop all)..."

# Wait for all background jobs so the trap fires on Ctrl+C
wait
