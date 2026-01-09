#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$repo_root/.env"

if [[ ! -f "$env_file" ]]; then
  echo "prebuild: missing .env file at $env_file" >&2
  exit 1
fi

camunda_version="${CAMUNDA_VERSION_OVERRIDE:-${1:-}}"
connectors_version="${CONNECTORS_VERSION_OVERRIDE:-${2:-}}"

if [[ -z "$camunda_version" ]]; then
  echo "Usage: CAMUNDA_VERSION_OVERRIDE=<version> CONNECTORS_VERSION_OVERRIDE=<version> $0" >&2
  echo "   or: $0 <camunda_version> [connectors_version]" >&2
  exit 1
fi

if [[ -z "$connectors_version" ]]; then
  connectors_version="$camunda_version"
fi

update_var() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$env_file"; then
    tmp_file="$(mktemp)"
    trap 'rm -f "$tmp_file"' EXIT
    awk -v k="$key" -v v="$value" '
      BEGIN { replaced = 0 }
      {
        if ($0 ~ "^" k "=") {
          print k "=" v
          replaced = 1
        } else {
          print
        }
      }
      END {
        if (!replaced) {
          print k "=" v
        }
      }
    ' "$env_file" >"$tmp_file"
    mv "$tmp_file" "$env_file"
    trap - EXIT
  else
    printf '%s=%s\n' "$key" "$value" >>"$env_file"
  fi
}

update_var "CAMUNDA_VERSION" "$camunda_version"
update_var "CONNECTORS_VERSION" "$connectors_version"

printf 'prebuild: set CAMUNDA_VERSION=%s, CONNECTORS_VERSION=%s in %s\n' \
  "$camunda_version" "$connectors_version" "$env_file"
