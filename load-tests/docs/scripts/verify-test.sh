#!/bin/bash
set -eo pipefail

usage() {
  cat <<'EOF'
Usage: verify-test.sh <namespace> [pod_timeout_seconds] [pod_ready_retries] [connectivity_timeout_seconds] [metrics_port]
Arguments:
  namespace: Kubernetes namespace where the load test is deployed
  pod_timeout_seconds: (optional) Timeout for waiting for pods to be ready in seconds (default: 60)
  pod_ready_retries: (optional) Number of retries for waiting for pods to be ready (default: 40)
  connectivity_timeout_seconds: (optional) Timeout for waiting for client connectivity in seconds (default: 1800)
  metrics_port: (optional) Port on which the client exposes metrics (default: 9600)
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
POD_TIMEOUT=${2:-60}
POD_READY_RETRIES=${3:-40}
CONNECTIVITY_TIMEOUT=${4:-1800}
METRICS_PORT=${5:-9600}
GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

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
  local max_retries="${POD_READY_RETRIES}"
  local retry_delay=10
  local wait_output

  for attempt in $(seq 1 "$max_retries"); do
    echo "Waiting for ${description} to be ready (attempt ${attempt}/${max_retries}, timeout: ${POD_TIMEOUT}s)..."
    wait_output=$(kubectl wait --for=condition=ready pod \
        -l "$label" \
        --timeout="${POD_TIMEOUT}s" -n "$NAMESPACE" 2>&1) && {
      echo "::group::Wait output"
      echo "$wait_output"
      echo "::endgroup::"
      echo "${description} are ready in $NAMESPACE"
      return 0
    }
    echo "::group::Wait output"
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

    out_of_cpu_pods="$(echo "$pods" | grep OutOfCpu | awk '{print $1}')"
    if [[ -n "$out_of_cpu_pods" ]]; then
        echo "Detected pods with OutOfCpu status, will delete them..."
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

# Port-forward to the clients service metrics endpoint
local_port=$((METRICS_PORT + RANDOM % 1000))
kubectl port-forward "svc/clients" "${local_port}:${METRICS_PORT}" -n "$NAMESPACE" &
pf_pid=$!
trap 'kill "$pf_pid" 2>/dev/null || true; wait "$pf_pid" 2>/dev/null || true' EXIT
sleep 5  # wait for port-forward to establish

# Check that the client has successfully connected to the gateway.
# app_connected >= 1 confirms that the topology was received (i.e. the
# client authenticated and connected successfully, regardless of REST or gRPC).
interval=30
max_attempts=$((CONNECTIVITY_TIMEOUT / interval))
attempts=0
verified=false

while [[ $attempts -lt $max_attempts ]]; do
  count=$( { curl -s "http://localhost:${local_port}/metrics" 2>/dev/null || true; } \
    | { grep '^app_connected ' || true; } \
    | awk '{print $2}' \
    | cut -d. -f1)

  if [[ -n "$count" ]] && [[ "$count" -ge 1 ]]; then
    echo "Namespace $NAMESPACE: client connected to gateway (app.connected=$count)"
    verified=true
    break
  fi

  attempts=$((attempts + 1))
  echo "Namespace $NAMESPACE: waiting for gateway connectivity (attempt $attempts/$max_attempts)"
  sleep "$interval"
done

kill $pf_pid 2>/dev/null || true
wait $pf_pid 2>/dev/null || true

if [[ "$verified" != "true" ]]; then
  echo "::error::Namespace $NAMESPACE client did not connect to gateway within timeout (app.connected metric never reached 1)"
  echo "status=failure" >> "$GITHUB_OUTPUT"
  exit 1
fi

echo "status=success" >> "$GITHUB_OUTPUT"
