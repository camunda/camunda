#!/bin/bash

set -euo pipefail

# Ensure these environment variables are set before running the script:
# export GITHUB_TOKEN="your_github_token"

# Check if required environment variables are set
if [ -z "${GITHUB_TOKEN:-}" ]; then
    echo "Error: GITHUB_TOKEN environment variable is not set."
    exit 1
fi

LOCAL_RENOVATE_CONFIG="../.././.github/renovate.json"
REPO_NAME="camunda/camunda"
LOCAL_CACHE_DIR="$(pwd)/renovate_cache"
LOCAL_BASE_DIR="$(pwd)/renovate"

if [ ! -f "$LOCAL_RENOVATE_CONFIG" ]; then
    echo "Error: Local Renovate config file '$LOCAL_RENOVATE_CONFIG' not found."
    exit 1
fi
mkdir -p "${LOCAL_CACHE_DIR}"
mkdir -p "${LOCAL_BASE_DIR}"

echo "Processing repository: ${REPO_NAME}"
echo "Using Renovate configuration from: ${LOCAL_RENOVATE_CONFIG}"
echo "Using local Renovate cache at: ${LOCAL_CACHE_DIR}"

start_time=$(date +%s)
echo "Renovate run started at: $(date)"

# Run renovate
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e LOG_LEVEL="debug" \
  -e RENOVATE_DRY_RUN="full" \
  -e RENOVATE_LOG_FORMAT="json" \
  -e RENOVATE_PLATFORM="github" \
  -e RENOVATE_TOKEN="${GITHUB_TOKEN}" \
  -e RENOVATE_REPOSITORIES="${REPO_NAME}" \
  -e RENOVATE_REQUIRE_CONFIG="ignored" \
  -e RENOVATE_CONFIG_FILE="/usr/src/app/mounted-renovate-config.json" \
  -v "$(pwd)/${LOCAL_RENOVATE_CONFIG}:/usr/src/app/mounted-renovate-config.json:ro" \
  -e RENOVATE_BASE_DIR="/tmp/renovate" \
  -v "${LOCAL_BASE_DIR}:/tmp/renovate" \
  -e RENOVATE_CACHE_DIR="/cache/renovate" \
  -v "${LOCAL_CACHE_DIR}:/cache/renovate" \
  renovate/renovate | tee "/tmp/${REPO_NAME//\//_}_$(date +%Y%m%d_%H%M%S).txt"

end_time=$(date +%s)
echo "Renovate run finished at: $(date)"
duration=$((end_time - start_time))
echo "Total runtime: ${duration} seconds"
minutes=$((duration / 60))
seconds=$((duration % 60))
echo "Total runtime: ${minutes} minutes and ${seconds} seconds"
