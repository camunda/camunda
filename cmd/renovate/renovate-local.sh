#!/bin/bash

set -uo pipefail

# Ensure Github CLI is installed before running this script
if ! command -v gh >/dev/null 2>&1; then
  echo "Error: GitHub CLI (gh) is not installed. Please install it before running this script: https://cli.github.com/"
  exit 1
fi

# Login to GitHub CLI if not already authenticated
if [ ! "$(gh auth status)" ]; then
  gh auth login -p https -h github.com -w
fi

# Autodetect a local Renovate config file if not provided
if [ -z "${LOCAL_RENOVATE_CONFIG:-}" ] || [ ! -f "$LOCAL_RENOVATE_CONFIG" ]; then
  found_config=$(find "$(pwd)" -type f -name 'renovate.json*' | grep -E -o "$(pwd)/(.github/)?renovate\.json[^/]*" | head -n 1)
  if [ -n "$found_config" ]; then
    LOCAL_RENOVATE_CONFIG="$found_config"
    echo "Found alternative Renovate config: $LOCAL_RENOVATE_CONFIG"
  else
    echo "Error: No Renovate config file found (searched for 'renovate.json*')."
    exit 1
  fi
fi

# detect the repository name from the git remote URL if not provided
if [ -z "${REPO_NAME:-}" ]; then
  REPO_NAME=$(gh repo view --json name,owner -q '.owner.login + "/" + .name' )
fi

set -e

GITHUB_TOKEN=$(gh auth token)
LOCAL_CACHE_DIR="$(pwd)/renovate_cache"
LOCAL_BASE_DIR="$(pwd)/renovate"

if [ ! -f "$LOCAL_RENOVATE_CONFIG" ]; then
    echo "Error: Local Renovate config file '$LOCAL_RENOVATE_CONFIG' not found."
    exit 1
fi
mkdir -p "${LOCAL_CACHE_DIR}"
mkdir -p "${LOCAL_BASE_DIR}"

echo "Processing repository: ${REPO_NAME}"
echo "Using local renovate configuration from: ${LOCAL_RENOVATE_CONFIG}"
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
  -v "${LOCAL_RENOVATE_CONFIG}:/usr/src/app/mounted-renovate-config.json:ro" \
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
