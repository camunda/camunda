#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Validates that the Docker Hub image tags requested for a load test (orchestration,
# optimize, identity, connectors) actually exist before the deploy job wastes time on
# a namespace that can never come up. Retries transient registry errors, but fails fast
# on a genuinely missing image.
#
# Usage: load-test-validate-docker-image-tags.sh <orchestration-tag> <optimize-tag> <identity-tag> <connectors-tag> [enable-optimize]
#   orchestration-tag   Docker Hub tag for camunda/camunda (empty to skip)
#   optimize-tag        Docker Hub tag for camunda/optimize (empty to skip)
#   identity-tag        Docker Hub tag for camunda/identity (empty to skip)
#   connectors-tag      Docker Hub tag for camunda/connectors (empty to skip)
#   enable-optimize     Whether Optimize is part of this deployment (true/false, default: false)

set -euo pipefail

orchestration_tag="$1"
optimize_tag="$2"
identity_tag="$3"
connectors_tag="$4"
enable_optimize="${5:-false}"

validate_image() {
  local image="$1"
  local max_retries=3
  local retry_delay=5
  local attempt=1
  local manifest_output

  while true; do
    echo "Validating Docker image ${image} (attempt ${attempt}/${max_retries})"

    # Use the `if`-form so a non-zero `docker manifest inspect` does NOT abort the script under
    # `set -e`: a bare `manifest_output=$(docker manifest inspect ...)` assignment inherits the
    # command's failure status, which `set -e` treats as fatal — skipping the retry/classification
    # below and turning every transient registry error (e.g. Docker Hub rate limiting) into a
    # hard failure.
    if manifest_output=$(docker manifest inspect "${image}" 2>&1 >/dev/null); then
      echo "Docker image ${image} exists"
      return 0
    fi

    # Distinguish a genuinely missing image from transient errors, retrying only the latter.
    if echo "${manifest_output}" | grep -qiE "manifest unknown|no such manifest|not found"; then
      echo "::error::Docker image ${image} does not exist on Docker Hub"
      return 1
    fi

    if [ "${attempt}" -ge "${max_retries}" ]; then
      echo "::error::Failed to validate Docker image ${image} after ${max_retries} attempts: ${manifest_output}"
      return 1
    fi

    echo "Transient error (attempt ${attempt}/${max_retries}), retrying in ${retry_delay}s: ${manifest_output}"
    sleep "${retry_delay}"
    attempt=$((attempt + 1))
  done
}

if [[ -n "${orchestration_tag}" ]]; then
  validate_image "camunda/camunda:${orchestration_tag}"
fi

if [[ "${enable_optimize}" == "true" && -n "${optimize_tag}" ]]; then
  validate_image "camunda/optimize:${optimize_tag}"
fi

if [[ -n "${identity_tag}" ]]; then
  validate_image "camunda/identity:${identity_tag}"
fi

if [[ -n "${connectors_tag}" ]]; then
  validate_image "camunda/connectors:${connectors_tag}"
fi
