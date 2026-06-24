#!/bin/bash

set -uo pipefail

# Ensure Github CLI is installed before running this script
if ! command -v gh >/dev/null 2>&1; then
  echo "Error: GitHub CLI (gh) is not installed. Please install it before running this script: https://cli.github.com/"
  exit 1
fi

# Ensure jq is installed (used for safe JSON construction of secrets)
if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is not installed. Please install it (e.g. 'brew install jq' or 'apt install jq')."
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

# Vault path and field mapping for Renovate secrets.
# Add entries to vault_field_for_secret() when new {{ secrets.XYZ }} refs are
# added to the Renovate config.
VAULT_MOUNT="secret"
VAULT_SECRET_PATH="products/camunda/ci/github-actions"

vault_field_for_secret() {
  case "$1" in
    INFRA_MINIMUS_REGISTRY_TOKEN) echo "REGISTRY_MINIMUS_PSW" ;;
    *) echo "" ;;
  esac
}

# Resolve secrets referenced in the config: fetch from Vault when authenticated,
# otherwise fall back to dummy values so the dry-run can still proceed.
RENOVATE_SECRETS="{}"
secret_names=$(grep -oE '\{\{\s*secrets\.[A-Z_a-z0-9]+\s*\}\}' "$LOCAL_RENOVATE_CONFIG" \
  | sed 's/.*secrets\.\([A-Za-z0-9_]*\).*/\1/' | sort -u || true)
if [ -n "$secret_names" ]; then
  vault_ok=false
  if command -v vault >/dev/null 2>&1 && vault token lookup >/dev/null 2>&1; then
    vault_ok=true
    echo "Vault authenticated — will fetch real secret values"
  else
    echo "Vault not available — using dummy secret values (run 'vault login -method=oidc' for real values)"
  fi

  RENOVATE_SECRETS="{}"
  for name in $secret_names; do
    value="dummy-local-value"
    vault_field=$(vault_field_for_secret "$name")

    if [ "$vault_ok" = true ] && [ -n "$vault_field" ]; then
      fetched=$(vault kv get -mount="$VAULT_MOUNT" -field="$vault_field" "$VAULT_SECRET_PATH" 2>/dev/null) || true
      if [ -n "$fetched" ]; then
        value="$fetched"
        echo "  ✓ $name (Vault field: $vault_field)"
      else
        echo "  ✗ $name: Vault fetch failed for field '$vault_field', using dummy value"
      fi
    elif [ "$vault_ok" = true ]; then
      echo "  ? $name: no Vault mapping defined, using dummy value"
    fi

    RENOVATE_SECRETS=$(echo "$RENOVATE_SECRETS" | jq --arg k "$name" --arg v "$value" '. + {($k): $v}')
  done
fi

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
  -e RENOVATE_SECRETS="${RENOVATE_SECRETS}" \
  ${RENOVATE_ENABLED_MANAGERS:+-e RENOVATE_ENABLED_MANAGERS="${RENOVATE_ENABLED_MANAGERS}"} \
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
