#!/usr/bin/env bash
# Run the DMN evaluation k6 load test as an isolated in-cluster Job.
#
# The generator runs in a pod kept OFF the broker nodes (see job.yaml), so it
# does not contend for the Orchestration Cluster's CPU — the whole point of
# running it in-cluster instead of from a laptop.
#
# Prereqs:
#   * kubectl context/namespace point at the target cluster.
#   * The decision is deployed (see README: deploy resources/small_decision.dmn).
#
# Usage:
#   ./run.sh                                  # defaults: small_decision, 100 rps, 60s
#   DMN_RATE=3334 DMN_DURATION=5m ./run.sh    # ~200k/min for 5 minutes
#   DMN_RATE=6667 DMN_MAX_VUS=1000 ./run.sh   # ~400k/min
#
# Recognised overrides (exported before running): DMN_RATE, DMN_DURATION,
# DMN_MAX_VUS, DMN_DECISION_ID, DMN_MIX, DMN_VARIABLES, CAMUNDA_BASE_URL,
# CAMUNDA_BASIC_AUTH.
set -euo pipefail
cd "$(dirname "$0")"

JOB=k6-dmn-evaluation
NS=()
[[ -n "${KUBECTL_NAMESPACE:-}" ]] && NS=(-n "$KUBECTL_NAMESPACE")

echo "==> (re)creating ConfigMap k6-dmn-scripts from dmn-evaluation.js"
kubectl "${NS[@]}" create configmap k6-dmn-scripts \
  --from-file=dmn-evaluation.js \
  --dry-run=client -o yaml | kubectl "${NS[@]}" apply -f -

echo "==> deleting any previous run"
kubectl "${NS[@]}" delete job "$JOB" --ignore-not-found

# Build a JSON env-override list from any recognised vars that are set.
entries=()
for v in DMN_RATE DMN_DURATION DMN_MAX_VUS DMN_DECISION_ID DMN_MIX DMN_VARIABLES CAMUNDA_BASE_URL CAMUNDA_BASIC_AUTH; do
  if [[ -n "${!v:-}" ]]; then
    entries+=("$(printf '{"name":"%s","value":%s}' "$v" "$(printf '%s' "${!v}" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')")")
  fi
done

if [[ ${#entries[@]} -eq 0 ]]; then
  echo "==> applying Job (defaults)"
  kubectl "${NS[@]}" apply -f job.yaml
else
  env_json="[$(IFS=,; echo "${entries[*]}")]"
  echo "==> applying Job with env overrides: $env_json"
  # Strategic merge: containers merge by `name`, env entries merge by `name`.
  patch="{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"k6\",\"env\":$env_json}]}}}}"
  kubectl patch --local -f job.yaml --type strategic -p "$patch" -o yaml \
    | kubectl "${NS[@]}" apply -f -
fi

echo "==> waiting for pod to start"
kubectl "${NS[@]}" wait --for=condition=ready pod -l job-name="$JOB" --timeout=120s || true

echo "==> streaming k6 output (Ctrl-C to detach; the Job keeps running)"
kubectl "${NS[@]}" logs -f "job/$JOB" || true

echo "==> done. Clean up with:"
echo "    kubectl ${NS[*]} delete job $JOB && kubectl ${NS[*]} delete configmap k6-dmn-scripts"
