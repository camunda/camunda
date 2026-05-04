#!/usr/bin/env bash

# Waits until Tasklist and Operate endpoints are reachable on the local cluster.
# Pings the cluster every 5s for up to 60 attempts. => Timeout after 5min.

set -euo pipefail

readonly max_attempts=60
readonly wait_seconds=5

for attempt in $(seq 1 "$max_attempts"); do
  if curl -sf http://localhost:8080/tasklist >/dev/null \
    && curl -sf http://localhost:8080/operate >/dev/null \; then
    echo "Orchestration Cluster is ready."
    exit 0
  fi

  echo "Waiting for Orchestration Cluster to become ready... (${attempt}/${max_attempts})"
  sleep "$wait_seconds"
done

echo "Orchestration Cluster failed to become ready in time."
exit 1