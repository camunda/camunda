#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Checks that the Docker Hub images for a release load test exist, using the image names
# of the chart of the target folder (camunda/zeebe, operate and tasklist for stable-87,
# camunda/camunda otherwise, and camunda/connectors-bundle for Connectors). Retries
# transient registry errors and fails at once on a missing image.
#
# Usage: load-test-validate-release-image-tags.sh <folder> <orchestration-tag> <optimize-tag> <identity-tag> <connectors-tag>
#   An empty optimize, identity or connectors tag is skipped.

set -euo pipefail

folder="$1"
orchestration_tag="$2"
optimize_tag="${3:-}"
identity_tag="${4:-}"
connectors_tag="${5:-}"

validate_image() {
  local image="$1" max_retries=3 retry_delay=5 attempt=1 output

  while true; do
    echo "Validating Docker image ${image} (attempt ${attempt}/${max_retries})"
    if output=$(docker manifest inspect "${image}" 2>&1 >/dev/null); then
      echo "Docker image ${image} exists"
      return 0
    fi
    if echo "${output}" | grep -qiE "manifest unknown|no such manifest|not found"; then
      echo "::error::Docker image ${image} does not exist on Docker Hub"
      return 1
    fi
    if [[ "${attempt}" -ge "${max_retries}" ]]; then
      echo "::error::Failed to validate Docker image ${image} after ${max_retries} attempts: ${output}"
      return 1
    fi
    echo "Transient error, retrying in ${retry_delay}s: ${output}"
    sleep "${retry_delay}"
    attempt=$((attempt + 1))
  done
}

status=0
check() {
  validate_image "$1" || status=1
}

if [[ "${folder}" == "stable-87" ]]; then
  for name in zeebe operate tasklist; do
    check "camunda/${name}:${orchestration_tag}"
  done
else
  check "camunda/camunda:${orchestration_tag}"
fi
[[ -n "${optimize_tag}" ]] && check "camunda/optimize:${optimize_tag}"
[[ -n "${identity_tag}" ]] && check "camunda/identity:${identity_tag}"
[[ -n "${connectors_tag}" ]] && check "camunda/connectors-bundle:${connectors_tag}"
exit "${status}"
