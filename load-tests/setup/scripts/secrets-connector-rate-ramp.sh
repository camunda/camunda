#!/usr/bin/env bash
# Steps the secrets-connector-e2e benchmark's Starter rate through a list of concurrency levels
# on an already-deployed namespace, holding each level for a fixed dwell time before moving to the
# next. This is the "concurrency ramp" test from issue #56590's benchmark plan (1 -> 5 -> 10 -> 25
# -> 50 -> 100): each step is a separate `helm upgrade --reuse-values` against the load-test-setup
# release, so a Grafana time range spanning the whole run shows a clean step function per level,
# comparable to (and isolated from) the other steps.
#
# Run from the scaffolded namespace directory newLoadTest.sh created (the one "make
# secrets-connector" was run from), so ./charts/load-test-setup resolves to the chart that was
# actually deployed there:
#
#   cd load-tests/setup/<name>
#   ../scripts/secrets-connector-rate-ramp.sh <namespace> [dwell-seconds] [rate ...]
#
# Example, using the default rates and a 5-minute dwell per step:
#   ../scripts/secrets-connector-rate-ramp.sh c8-ajanoni-secrets-connector-e2e 300
#
# Example, with custom rates:
#   ../scripts/secrets-connector-rate-ramp.sh c8-ajanoni-secrets-connector-e2e 300 1 10 50
#
# Prerequisites: the secrets-connector-e2e scenario must already be deployed to <namespace> (see
# load-tests/docs/secrets-connector-benchmark.md), and the caller's kubeconfig/helm context must
# already point at the cluster hosting it.

set -euo pipefail

namespace="${1:?Usage: secrets-connector-rate-ramp.sh <namespace> [dwell-seconds] [rate ...]}"
dwell_seconds="${2:-300}"
shift $(( $# < 2 ? $# : 2 ))
if [ "$#" -eq 0 ]; then
  rates=(1 5 10 25 50 100)
else
  rates=("$@")
fi

chart_dir="charts/load-test-setup"
if [ ! -d "${chart_dir}" ]; then
  echo "ERROR: ${chart_dir} not found. Run this script from the scaffolded namespace directory" \
       "(the one newLoadTest.sh created, where 'make secrets-connector' was run)." >&2
  exit 1
fi

echo "Ramping load-tester.starter.rate through: ${rates[*]} (namespace: ${namespace}, dwell: ${dwell_seconds}s per step)"

for rate in "${rates[@]}"; do
  echo "--- Step: rate=${rate} ---"
  helm upgrade load-test-setup "${chart_dir}" \
    --reuse-values \
    --namespace "${namespace}" \
    --set "load-tester.starter.rate=${rate}"
  echo "Holding at rate=${rate} for ${dwell_seconds}s (warm-up + measure + cool-down)..."
  sleep "${dwell_seconds}"
done

echo "Rate ramp complete. Use the Grafana dashboard's time range to compare RPS/latency/error-rate/cache-hit-ratio across steps."
