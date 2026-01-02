#!/usr/bin/env bash
#
# cleanup-indexes.sh — Delete Elasticsearch indexes older than a TTL for a given prefix.
#
# Usage:
#   ./cleanup-indexes.sh --prefix logs- --ttl 2h [--url http://localhost:9200] [--user elastic] [--pass changeme] [--dry-run]
#
# TTL supports s/m/h/d suffixes (e.g., 45m, 2h, 1d).
#

set -euo pipefail

log() { echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"; }
debug() { [[ "${DEBUG:-0}" == "1" ]] && echo "[DEBUG] $*"; }

# Error handler and usage
on_error() {
  local line=$1
  log "❌ Error on or near line ${line}. Enable --debug for more details."
  [[ -n "${CURL_LAST_CMD:-}" ]] && log "Last curl: ${CURL_LAST_CMD}"
}
trap 'on_error $LINENO' ERR

usage() {
  cat << EOF
Usage: $0 --prefix <prefix> --ttl <1h|30m|1d> [--url <url>] [--user <user>] [--pass <pass>] [--namespace <ns>] [--dry-run] [--debug]

Options:
  --prefix    Index prefix to match (e.g., logs-)
  --ttl       Time-to-live threshold; delete indices older than this (s/m/h/d)
  --url       Elasticsearch base URL (default: http://localhost:9200)
  --user      Username for basic auth
  --pass      Password for basic auth
  --elasticsearch-namespace Kubernetes namespace to fetch password using scripts/get-credientials-from-cluster.sh (used if --pass not given)
  --dry-run   Show what would be deleted, but do not delete
  --debug     Verbose debug logging
  -h, --help  Show this help and exit
EOF
}

require_cmd() {
  command -v "$1" > /dev/null 2>&1 || {
    log "❌ Required command not found: $1"
    exit 127
  }
}

# Curl wrapper capturing status and body
REQ_STATUS=""
REQ_BODY=""
CURL_LAST_CMD=""
curl_request() {
  local method=$1
  local url=$2
  shift 2
  local tmp
  tmp=$(mktemp)
  local code
  # Build a redacted representation of the curl command for error/debug logs
  local CURL_FLAGS="--silent --show-error --connect-timeout 5 --max-time 30 --retry 2 --retry-delay 1 --retry-connrefused"
  local auth_display=""
  if [[ ${#auth_args[@]} -gt 0 ]]; then
    auth_display="-u ${ES_USER}:******"
  fi
  local extra_quoted="$(printf "%q " "$@")"
  CURL_LAST_CMD="curl ${CURL_FLAGS} -X ${method} ${auth_display} \"${url}\" ${extra_quoted}"
  debug "cmd: ${CURL_LAST_CMD}"
  code=$(curl --silent --show-error --connect-timeout 5 --max-time 30 \
    --retry 2 --retry-delay 1 --retry-connrefused \
    -o "$tmp" -w "%{http_code}" -X "$method" "${auth_args[@]}" "$url" "$@") || true
  if [[ "$code" =~ ^[0-9]{3}$ ]]; then
    REQ_STATUS="$code"
  else
    REQ_STATUS="000" # network/transport error
  fi
  REQ_BODY="$(cat "$tmp")"
  rm -f "$tmp"
  debug "HTTP ${method} ${url} -> ${REQ_STATUS}"
  return 0
}

PREFIX=""
TTL=""
ES_URL="http://localhost:9200"
ES_USER=""
ES_PASS=""
NAMESPACE=""
DRY_RUN=0

# ---- argument parsing ----
while [[ $# -gt 0 ]]; do
  case "$1" in
    --prefix)
      PREFIX="$2"
      shift 2
      ;;
    --ttl)
      TTL="$2"
      shift 2
      ;;
    --url)
      ES_URL="$2"
      shift 2
      ;;
    --user)
      ES_USER="$2"
      shift 2
      ;;
    --pass)
      ES_PASS="$2"
      shift 2
      ;;
    --elasticsearch-namespace)
      ELASTICSEARCH_NAMESPACE="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --debug)
      DEBUG=1
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      log "❌ Unknown arg: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$PREFIX" || -z "$TTL" ]]; then
  usage
  exit 1
fi

# Validate dependencies
require_cmd curl
require_cmd jq

# ---- helpers ----
if [[ -z "$ES_PASS" && -n "$ELASTICSEARCH_NAMESPACE" ]]; then
  if [[ -x "scripts/get-credientials-from-cluster.sh" ]]; then
    ES_PASS="$(scripts/get-credientials-from-cluster.sh --namespace "$ELASTICSEARCH_NAMESPACE")"
  else
    log "❌ scripts/get-credientials-from-cluster.sh not found or not executable"
    exit 1
  fi
fi

auth_args=()
[[ -n "$ES_USER" && -n "$ES_PASS" ]] && auth_args=(-u "${ES_USER}:${ES_PASS}")

ttl_to_seconds() {
  case "$1" in
    *d) echo $((${1%d} * 86400)) ;;
    *h) echo $((${1%h} * 3600)) ;;
    *m) echo $((${1%m} * 60)) ;;
    *s) echo $((${1%s})) ;;
    *) echo "$1" ;;
  esac
}

if ! [[ "$TTL" =~ ^[0-9]+([smhd])$ ]]; then
  log "❌ Invalid TTL: '$TTL'. Expected format like 45m, 2h, 1d, 30s"
  exit 1
fi

TTL_SECS=$(ttl_to_seconds "$TTL")
NOW_MS=$(($(date +%s) * 1000))

# ---- main ----
log "🔍 Checking indexes matching '${PREFIX}*' older than ${TTL}"
curl_request GET "${ES_URL}/_cat/indices/${PREFIX}*?h=index"
if ! [[ "$REQ_STATUS" =~ ^2..$ ]]; then
  log "❌ Failed to list indices (HTTP $REQ_STATUS)"
  [[ -n "$CURL_LAST_CMD" ]] && echo "  curl> $CURL_LAST_CMD"
  [[ -n "$REQ_BODY" ]] && echo "$REQ_BODY" | sed 's/^/  > /'
  exit 1
fi
INDICES="$REQ_BODY"

if [[ -z "$INDICES" ]]; then
  log "✅ No indexes found for prefix '${PREFIX}'"
  exit 0
fi

declare -a EXPIRED=()
declare -a DELETED=()
declare -a FAILED=()
declare -a FOUND=()
declare -a NOT_FOUND=()

# Bulk fetch settings for all indices once and compute expired set
curl_request GET "${ES_URL}/${PREFIX}*/_settings?filter_path=*.settings.index.creation_date"
if ! [[ "$REQ_STATUS" =~ ^2..$ ]]; then
  log "❌ Failed to bulk fetch settings (HTTP $REQ_STATUS)"
  [[ -n "$CURL_LAST_CMD" ]] && echo "  curl> $CURL_LAST_CMD"
  [[ -n "$REQ_BODY" ]] && echo "$REQ_BODY" | sed 's/^/  > /'
  exit 1
fi

THRESHOLD_MS=$((NOW_MS - TTL_SECS * 1000))
# Build EXPIRED array from the bulk settings payload
while IFS= read -r idx; do
  [[ -z "$idx" ]] && continue
  EXPIRED+=("$idx")
done < <(echo "$REQ_BODY" | jq -r --argjson TH "$THRESHOLD_MS" '
  to_entries
  | map(select((.value.settings.index.creation_date|tonumber) < $TH))
  | map(.key)
  | .[]?')

if ((${#EXPIRED[@]} == 0)); then
  log "✅ No expired indexes found."
  exit 0
fi

log "🗑 Found ${#EXPIRED[@]} expired indexes:"
for IDX in "${EXPIRED[@]}"; do echo "  - $IDX"; done

if ((DRY_RUN == 1)); then
  log "🔎 Verifying existence of expired indexes (dry run)..."
  for IDX in "${EXPIRED[@]}"; do
    curl_request HEAD "${ES_URL}/${IDX}"
    case "$REQ_STATUS" in
      200 | 204)
        log "✅ ${IDX} exists"
        FOUND+=("$IDX")
        ;;
      404)
        log "ℹ️  ${IDX} not found"
        NOT_FOUND+=("$IDX")
        ;;
      *)
        log "⚠️  ${IDX} existence check failed (HTTP $REQ_STATUS)"
        ;;
    esac
  done
  log "🧪 Dry run only — no deletions performed."
  log "📊 Summary: matched=${#EXPIRED[@]} found=${#FOUND[@]} not_found=${#NOT_FOUND[@]}"
  exit 0
fi

# Perform bulk deletions in batches to avoid URL length limits
FOUND+=("${EXPIRED[@]}")
BATCH_SIZE=50
TOTAL_EXPIRED=${#EXPIRED[@]}
START_INDEX=0
while (( START_INDEX < TOTAL_EXPIRED )); do
  END_INDEX=$((START_INDEX + BATCH_SIZE))
  (( END_INDEX > TOTAL_EXPIRED )) && END_INDEX=$TOTAL_EXPIRED
  # Build batch
  batch=("${EXPIRED[@]:START_INDEX:END_INDEX-START_INDEX}")
  if ((${#batch[@]} == 0)); then
    break
  fi
  csv="$(IFS=,; printf '%s' "${batch[*]}")"
  log "Deleting ${#batch[@]} indexes in bulk..."
  curl_request DELETE "${ES_URL}/${csv}"
  case "$REQ_STATUS" in
    200 | 202)
      for b in "${batch[@]}"; do DELETED+=("$b"); done
      ;;
    404)
      for b in "${batch[@]}"; do NOT_FOUND+=("$b"); done
      ;;
    *)
      log "❌ Failed bulk delete (HTTP $REQ_STATUS) for batch starting at $START_INDEX"
      [[ -n "$CURL_LAST_CMD" ]] && echo "  curl> $CURL_LAST_CMD"
      [[ -n "$REQ_BODY" ]] && echo "$REQ_BODY" | sed 's/^/  > /'
      for b in "${batch[@]}"; do FAILED+=("$b"); done
      ;;
  esac
  START_INDEX=$END_INDEX
done

log "🏁 Cleanup complete."
log "📊 Summary: matched=${#EXPIRED[@]} found=${#FOUND[@]} not_found=${#NOT_FOUND[@]} deleted=${#DELETED[@]} failed=${#FAILED[@]}"
if ((${#FAILED[@]} > 0)); then
  log "❗ Failures:"
  for f in "${FAILED[@]}"; do echo "  - $f"; done
  exit 2
fi
