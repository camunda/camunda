#!/bin/bash

LOCAL_RENOVATE_CONFIG="./.github/renovate.json"
REPO_NAME="camunda/camunda"
LOCAL_CACHE_DIR="$(pwd)/renovate_cache"

if [ ! -f "$LOCAL_RENOVATE_CONFIG" ]; then
    echo "Error: Local Renovate global config file '$LOCAL_RENOVATE_CONFIG' not found."
    exit 1
fi
mkdir -p "${LOCAL_CACHE_DIR}"

echo "Processing repository: ${REPO_NAME}"
echo "Using Renovate global configuration from: ${LOCAL_RENOVATE_CONFIG}"
echo "Using local Renovate cache at: ${LOCAL_CACHE_DIR}"

start_time=$(date +%s)
echo "Renovate run started at: $(date)"

# run renovate
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e LOG_LEVEL="debug" \
  -e RENOVATE_DRY_RUN="full" \
  -e RENOVATE_LOG_FORMAT="json" \
  -e RENOVATE_PLATFORM="github" \
  -e RENOVATE_TOKEN="${GITHUB_TOKEN}" \
  -e RENOVATE_REPOSITORIES="${REPO_NAME}" \
  -e RENOVATE_CONFIG_FILE="/usr/src/app/mounted-renovate-global-config.json" \
  -v "$(pwd)/${LOCAL_RENOVATE_CONFIG}:/usr/src/app/mounted-renovate-global-config.json:ro" \
  -e RENOVATE_CACHE_DIR="/cache/renovate" \
  -v "${LOCAL_CACHE_DIR}:/cache/renovate" \
  \
  renovate/renovate | tee "/tmp/camunda_$(date +%Y%m%d_%H%M%S).txt"

end_time=$(date +%s)
echo "Renovate run finished at: $(date)"
duration=$((end_time - start_time))
echo "Total runtime: ${duration} seconds"
minutes=$((duration / 60))
seconds=$((duration % 60))
echo "Total runtime: ${minutes} minutes and ${seconds} seconds"
