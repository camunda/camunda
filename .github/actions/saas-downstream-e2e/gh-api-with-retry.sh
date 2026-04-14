#!/usr/bin/env bash
set -euo pipefail

gh_api_with_retry() {
  local endpoint="$1"
  local max_attempts="${2:-8}"
  local sleep_s="${3:-2}"
  local attempt=1
  local out

  while true; do
    if out="$(gh api "$endpoint" 2>&1)"; then
      printf '%s' "$out"
      return 0
    fi

    if echo "$out" | grep -Eq 'HTTP 502|HTTP 503|HTTP 504|TLS handshake timeout|timeout|temporarily unavailable|connection reset by peer|EOF'; then
      if (( attempt >= max_attempts )); then
        echo "gh api failed after ${attempt}/${max_attempts} attempts for $endpoint" >&2
        echo "$out" >&2
        return 1
      fi
      echo "Transient gh/api error (attempt ${attempt}/${max_attempts}) for $endpoint:" >&2
      echo "$out" >&2
      sleep "$sleep_s"
      attempt=$((attempt + 1))
      sleep_s=$((sleep_s * 2))
      continue
    fi

    echo "Non-retryable gh/api error for $endpoint:" >&2
    echo "$out" >&2
    return 1
  done
}
