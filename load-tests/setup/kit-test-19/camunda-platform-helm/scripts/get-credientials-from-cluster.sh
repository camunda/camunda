#!/usr/bin/env bash
set -Eeuo pipefail

# ---- helpers ----
log() {
  # Timestamped log line to stderr
  printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2
}

die() {
  log "ERROR: $*"
  exit 1
}

on_error() {
  local lineno="$1"
  local cmd="$2"
  log "Command failed at line ${lineno}: ${cmd}"
}

trap 'on_error ${LINENO} "${BASH_COMMAND}"' ERR

require_cmd() {
  local bin="$1"
  command -v "$bin" > /dev/null 2>&1 || die "Required command not found: $bin"
}

mask_secret() {
  # Partially mask a secret, showing only first 2 and last 2 characters
  local s="$1"
  local len=${#s}
  if [[ $len -le 4 ]]; then
    printf '%*s' "$len" '' | tr ' ' '*'
    return 0
  fi
  local prefix="${s:0:2}"
  local suffix="${s: -2}"
  local mid_len=$((len - 4))
  printf '%s' "$prefix"
  printf '%*s' "$mid_len" '' | tr ' ' '*'
  printf '%s' "$suffix"
}

NAMESPACE=""

# parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 --namespace <namespace>" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$NAMESPACE" ]]; then
  echo "Error: --namespace is required" >&2
  echo "Usage: $0 --namespace <namespace>" >&2
  exit 1
fi

require_cmd kubectl
require_cmd base64

SECRET_NAME="infra-credentials"
SECRET_KEY="elasticsearch-password"

# Verify namespace exists (gives clearer error than a failing jsonpath)
if ! kubectl get namespace "$NAMESPACE" > /dev/null 2>&1; then
  die "Namespace not found or inaccessible: $NAMESPACE"
fi

# Verify secret exists
if ! kubectl -n "$NAMESPACE" get secret "$SECRET_NAME" > /dev/null 2>&1; then
  # Provide a short hint of available secrets to aid debugging
  local_list=$(kubectl -n "$NAMESPACE" get secrets -o name 2> /dev/null || true)
  die "Secret '$SECRET_NAME' not found in namespace '$NAMESPACE'. Available secrets: ${local_list:-<none>}"
fi

# Create a temporary workspace and ensure cleanup
TMP_DIR=$(mktemp -d)
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

# Fetch the base64-encoded password field with helpful diagnostics
if ! kubectl -n "$NAMESPACE" get secret "$SECRET_NAME" -o jsonpath="{.data.$SECRET_KEY}" > "$TMP_DIR/pw.b64" 2> "$TMP_DIR/kubectl.err"; then
  log "kubectl stderr: $(tr -d '\r' < "$TMP_DIR/kubectl.err")"
  die "Failed to read key '$SECRET_KEY' from secret '$SECRET_NAME' in namespace '$NAMESPACE'"
fi

if ! [[ -s "$TMP_DIR/pw.b64" ]]; then
  # Show available keys to help the user correct the key name
  keys=$(kubectl -n "$NAMESPACE" get secret "$SECRET_NAME" -o jsonpath='{.data}' 2> /dev/null || true)
  die "Key '$SECRET_KEY' is empty or missing in secret '$SECRET_NAME'. Available data keys: ${keys:-<unknown>}"
fi

if ! PASSWORD=$(base64 --decode < "$TMP_DIR/pw.b64" 2> "$TMP_DIR/decode.err"); then
  log "base64 stderr: $(tr -d '\r' < "$TMP_DIR/decode.err")"
  die "Failed to base64-decode the password from secret '$SECRET_NAME'"
fi

if [[ -z "${PASSWORD// /}" ]]; then
  die "Decoded password is empty after reading '$SECRET_KEY' from '$SECRET_NAME' in namespace '$NAMESPACE'"
fi

MASKED_PASSWORD=$(mask_secret "$PASSWORD")
log "Retrieved ELASTIC_PASSWORD (masked): $MASKED_PASSWORD"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  : # Intentionally no-op for CI; callers should capture stdout and write to GITHUB_OUTPUT/ENV
fi
printf '%s\n' "$PASSWORD"
