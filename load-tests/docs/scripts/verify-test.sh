#!/bin/bash
set -eo pipefail

usage() {
  cat <<'EOF'
Usage: verify-test.sh <namespace> [wait_timeout_seconds] [wait_retries] [connectivity_timeout_seconds] [metrics_port]
Arguments:
  namespace: Kubernetes namespace where the load test is deployed
  wait_timeout_seconds: (optional) Timeout of each wait attempt in seconds (default: 30)
  wait_retries: (optional) Number of attempts for each wait (pods and k6 TestRuns) (default: 30)
  connectivity_timeout_seconds: (optional) Timeout for waiting for client connectivity in seconds (default: 900)
  metrics_port: (optional) Port on which the client exposes metrics (default: 9600)
Environment variables:
  PROMETHEUS_URL: Base URL of the Prometheus queried for k6 metrics (default: https://ci-monitor.benchmark.camunda.cloud)
  PROMETHEUS_USER: (optional) User for HTTP basic authentication to Prometheus
  PROMETHEUS_PASSWORD: (optional) Password for HTTP basic authentication to Prometheus
  K6_METRICS_TIMEOUT: Timeout for waiting for k6 metrics in Prometheus in seconds (default: 300)
EOF
}

if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
  usage
  exit 0
fi


if [ -z "$1" ]; then
  echo "Error: Missing namespace name."
  usage
  exit 1
fi

NAMESPACE=$1

WAIT_TIMEOUT=${2:-30}
WAIT_RETRIES=${3:-30}
CONNECTIVITY_TIMEOUT=${4:-900}
METRICS_PORT=${5:-9600}
PROMETHEUS_URL=${PROMETHEUS_URL:-https://ci-monitor.benchmark.camunda.cloud}
PROMETHEUS_USER=${PROMETHEUS_USER:-}
PROMETHEUS_PASSWORD=${PROMETHEUS_PASSWORD:-}
K6_METRICS_TIMEOUT=${K6_METRICS_TIMEOUT:-300}
GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

RETRY_DELAY=2

echo "--- Checking namespace: $NAMESPACE ---"
# Verify namespace exists
if ! kubectl get ns "$NAMESPACE" &>/dev/null; then
  echo "::error::Namespace $NAMESPACE does not exist"
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
fi

# Retry wrapper for kubectl wait. Pods may be rescheduled during the wait
# window, causing "NotFound" errors when kubectl wait tries to watch a pod
# whose name changed. Retrying re-resolves the label selector.
wait_for_pods() {
  local label="$1"
  local description="$2"
  local max_retries="${WAIT_RETRIES}"
  local retry_delay="${RETRY_DELAY}"
  local wait_output

  for attempt in $(seq 1 "$max_retries"); do
    echo "Waiting for ${description} to be ready (attempt ${attempt}/${max_retries}, timeout: ${WAIT_TIMEOUT}s)..."
    wait_output=$(kubectl wait --for=condition=ready pod \
        -l "$label" \
        --timeout="${WAIT_TIMEOUT}s" -n "$NAMESPACE" 2>&1) && {
      echo "::group::⇒ kubectl wait output"
      echo "$wait_output"
      echo "::endgroup::"
      echo "${description} are ready in $NAMESPACE"
      return 0
    }
    echo "::group::⇒ kubectl wait output"
    echo "$wait_output"
    echo "::endgroup::"

    # Retry on errors (pod rescheduled/timeouts);
    echo "::error::Not all ${description} are ready in $NAMESPACE"
    if [[ "$attempt" -lt "$max_retries" ]]; then
      echo "Pod wasn't ready yet. Retrying in ${retry_delay}s..."
      sleep "$retry_delay"
    fi

    # Get pod status
    pods="$(kubectl get pod --no-headers -o wide -n "$NAMESPACE")"
    echo "::group::Pods status"
    echo "$pods"
    echo "::endgroup::"

    out_of_cpu_pods="$(echo "$pods" | grep -iw outofcpu | awk '{print $1}')"
    if [[ -n "$out_of_cpu_pods" ]]; then
        echo "Detected pods with 'out of CPU' status, will delete them..."
        # Mitigation: sometimes, many pods are scheduled on the same node and the
        # kubelet fails to start them with a "OutOfCpu" error.
        # Although it looks like a Kubernetes bug, these pods stay and are being
        # taken into account by the `kubectl wait` call above, which then never
        # terminates successfully.
        # As a mitigation, we delete these OutOfCpu pods as they are dead anyway.
        echo "$out_of_cpu_pods" | xargs -t kubectl delete pod -n "$NAMESPACE"
    fi
  done

  echo "::error::Not all ${description} are ready in $NAMESPACE after ${max_retries} attempts"
  return 1
}

# Wait until every k6 TestRun is healthy.
# We consider the k6 tests to be OK if:
# 1. All TestRuns are in a healthy stage (started, stopped, finished)
# 2. None of the k6 pods have stopped unexpectedly (initializer pods may be
# stopped, they run for a short time and exit successfully)
wait_for_k6_testruns() {
  local testruns

  # A failing kubectl (missing CRD, RBAC denied, expired auth) must not be reported as "no TestRun".
  if ! testruns="$(kubectl get testruns.k6.io -n "$NAMESPACE" -o name)"; then
    echo "::error::Unable to list k6 TestRuns in $NAMESPACE"
    return 1
  fi
  if [[ -z "$testruns" ]]; then
    echo "No k6 TestRun deployed in $NAMESPACE, nothing to verify."
    return 0
  fi

  local max_attempts=$((WAIT_RETRIES * (WAIT_TIMEOUT + RETRY_DELAY) / RETRY_DELAY))
  local stages pending failed_pods

  local attempt=0
  while true; do
    attempt=$((attempt + 1))
    echo "Waiting for k6 TestRuns to be healthy (attempt ${attempt}/${max_attempts})..."
    if ! stages="$(kubectl get testruns.k6.io -n "$NAMESPACE" \
        -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.stage}{"\n"}{end}')"; then
      echo "::error::Unable to list k6 TestRuns in $NAMESPACE"
      return 1
    fi
    echo "::group::⇒ k6 TestRuns status"
    echo "$stages"
    echo "::endgroup::"

    if grep -Eq ' error$' <<< "$stages"; then
      echo "::error::A k6 TestRun in $NAMESPACE is unhealthy"
      return 1
    fi

    # Fast fail if any k6 pod has stopped unexpectedly.
    # Contrary to the other pods, the k6 pods may fail and never come back
    # ready again, so wait_for_pods may wait until its timeout if the pods
    # crashed.
    # Also, the TestRun status does not reveal a crashed runner: k6-operator
    # moves the TestRun to stopped/finished even when a runner failed.
    if ! failed_pods="$(kubectl get pod -n "$NAMESPACE" -l app=k6 \
        --field-selector=status.phase=Failed -o name)"; then
      echo "::error::Unable to list k6 pods in $NAMESPACE"
      return 1
    fi
    if [[ -n "$failed_pods" ]]; then
      echo "::error::Some k6 pods stopped unexpectedly in $NAMESPACE: ${failed_pods//$'\n'/ }"
      return 1
    fi

    pending="$(echo "$stages" | grep -Ev ' (started|stopped|finished)$' || true)"
    if [[ -z "$pending" ]]; then
      echo "k6 TestRuns are healthy in $NAMESPACE"
      break
    fi

    if [[ "$attempt" -ge "$max_attempts" ]]; then
      echo "::error::Not all k6 TestRuns are healthy in $NAMESPACE after ${max_attempts} attempts"
      return 1
    fi
    echo "k6 TestRuns not healthy yet. Retrying in ${RETRY_DELAY}s..."
    sleep "$RETRY_DELAY"
  done

  # A TestRun stays "started" while its runner pods are still pending or restarting, so the
  # stage alone does not show that they run. Pods of stopped or finished TestRuns are Completed
  # and never become Ready, so only the started TestRuns are waited for.
  local name stage
  while read -r name stage; do
    if [[ "$stage" == "started" ]]; then
      wait_for_pods "app=k6,runner=true,k6_cr=${name}" "k6 runner pods of TestRun ${name}" || return 1
    else
      echo "k6 TestRun ${name} is ${stage}, skipping its runner pods."
    fi
  done <<< "$stages"
}

# Wait until k6 reports metrics to Prometheus (k6_http_reqs_total >= 1).
verify_k6_metrics() {
  local testruns
  if ! testruns="$(kubectl get testruns.k6.io -n "$NAMESPACE" -o name)"; then
    echo "::error::Unable to list k6 TestRuns in $NAMESPACE"
    return 1
  fi
  if [[ -z "$testruns" ]]; then
    # No k6 TestRun deployed, nothing to verify.
    return 0
  fi

  local auth=()
  if [[ -n "$PROMETHEUS_USER" ]]; then
    auth=(--user "${PROMETHEUS_USER}:${PROMETHEUS_PASSWORD}")
  fi
  local max_attempts=$((K6_METRICS_TIMEOUT / RETRY_DELAY))
  local attempts=0
  local http_reqs=""

  while [[ $attempts -lt $max_attempts ]]; do
    attempts=$((attempts + 1))
    echo "::group::⇒ k6 metrics checks $attempts/$max_attempts"
    echo "Checking k6 metrics: $attempts/$max_attempts"
    http_reqs=$( { curl --max-time 20 -sf -G ${auth[@]+"${auth[@]}"} "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=sum(k6_http_reqs_total{namespace=\"${NAMESPACE}\", expected_response=\"true\"})" || true; } \
      | { jq -r '(.data.result[0].value[1] // 0) | tonumber | ceil' 2>/dev/null || true; })

    if [[ -n "$http_reqs" ]] && [[ "$http_reqs" -ge 1 ]]; then
      echo "::endgroup::"
      echo "Namespace $NAMESPACE: k6 reports metrics (k6_http_reqs_total=$http_reqs)"
      return 0
    fi

    echo "Namespace $NAMESPACE: waiting for k6 metrics (attempt $attempts/$max_attempts)"
    echo "::endgroup::"
    sleep "$RETRY_DELAY"
  done

  echo "::error::Namespace $NAMESPACE k6 did not report metrics to Prometheus within timeout (k6_http_reqs_total never reached 1)"
  return 1
}

# Wait for platform pods (camunda-platform helm chart)
if ! wait_for_pods "app=camunda-platform" "Camunda platform pods"; then
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
fi

# Wait for load test client pods (starter + workers from load test helm chart)
if ! wait_for_pods "app.kubernetes.io/component=zeebe-client" "load test client pods"; then
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
fi

# Wait for the k6 TestRuns (k6 tests of the load test helm chart), if any
if ! wait_for_k6_testruns; then
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
fi

# Check that the client has successfully connected to the gateway.
# app_connected >= 1 confirms that the topology was received (i.e. the
# client authenticated and connected successfully, regardless of REST or gRPC).
retry_delay="${RETRY_DELAY}"
max_attempts=$((CONNECTIVITY_TIMEOUT / RETRY_DELAY))
attempts=0
verified=false
app_connected=""

while [[ $attempts -lt $max_attempts ]]; do
  echo "::group::⇒ connectivity checks $attempts/$max_attempts"
  echo "Checking clients connectivity: $attempts/$max_attempts"
  # Port-forward to the clients service metrics endpoint
  local_port=$((METRICS_PORT + RANDOM % 1000))
  service="svc/clients"
  echo "Opening port-forward to $service via port $local_port..."
  kubectl port-forward "$service" "${local_port}:${METRICS_PORT}" -n "$NAMESPACE" &
  pf_pid=$!
  trap 'kill "$pf_pid" 2>/dev/null || true; wait "$pf_pid" 2>/dev/null || true' EXIT
  sleep 2  # wait for port-forward to establish

  app_connected=$( { curl -s "http://localhost:${local_port}/metrics" 2>/dev/null || true; } \
    | { grep '^app_connected ' || true; } \
    | awk '{print $2}' \
    | cut -d. -f1)

  echo "Stopping the port-forward..."
  kill $pf_pid 2>/dev/null || true
  wait $pf_pid 2>/dev/null || true
  attempts=$((attempts + 1))

  if [[ -n "$app_connected" ]] && [[ "$app_connected" -ge 1 ]]; then
    verified=true
    echo "::endgroup::"
    break
  fi

  echo "Namespace $NAMESPACE: waiting for gateway connectivity (attempt $attempts/$max_attempts)"
  echo "::endgroup::"
  sleep "$retry_delay"
done

if [[ "$verified" != "true" ]]; then
  echo "::error::Namespace $NAMESPACE client did not connect to gateway within timeout (app.connected metric never reached 1)"
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
else
  echo "Namespace $NAMESPACE: client connected to gateway (app.connected=$app_connected)"
fi

# Check that k6 reports metrics to Prometheus, if there are k6 TestRuns
if ! verify_k6_metrics; then
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
fi

echo "status=success" >> "$GITHUB_OUTPUT"
