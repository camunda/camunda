#!/usr/bin/env bash
# Run every query in queries.yaml against a Prometheus HTTP endpoint and emit
# a JSON object on stdout: {name: value, ...} with failed/empty queries
# omitted entirely. Run `./loadTestMetrics.sh --help` for full usage.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: loadTestMetrics.sh <namespace> [duration_seconds] [endpoint] [extra_curl_opts]

Arguments:
  namespace          Substituted for $NAMESPACE in queries (required).
  duration_seconds   Positive integer; substituted as ${n}s for $DURATION_S. Default: 600.
  endpoint           Prometheus base URL. Default: http://localhost:9090
                     (assumes a `kubectl port-forward` is open in another terminal).
  extra_curl_opts    Free-form curl options string, e.g. `--user "u:p"`.
                     Pass "" if not needed. Default: "".

Options:
  -h, --help         Show this help message.

Examples:
  # Local dev, port-forward already open:
  ./loadTestMetrics.sh c8-pgoyal-quicker-pr-1234

  # CI against the LDAP-protected ingress:
  ./loadTestMetrics.sh \
    "$NAMESPACE" "$DURATION_SECONDS" \
    "https://ci-monitor.benchmark.camunda.cloud" \
    "--user $PROM_USER:$PROM_PASS" > /tmp/results.json
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "${1:-}" ]]; then
  echo "Error: Missing <namespace>." >&2
  usage >&2
  exit 1
fi

### --- Args ---
NAMESPACE="$1"
DURATION_SECONDS="${2:-600}"
ENDPOINT="${3:-http://localhost:9090}"
EXTRA_OPTS="${4-}"

if (( ${#NAMESPACE} > 63 )) || ! [[ "$NAMESPACE" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
  echo "Error: namespace '$NAMESPACE' must be a valid Kubernetes DNS label (max 63 characters; lowercase alphanumeric or '-', and must start and end with an alphanumeric character)." >&2
  exit 1
fi

if ! [[ "$DURATION_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "Error: duration_seconds '$DURATION_SECONDS' must be a positive integer." >&2
  exit 1
fi

DURATION_S="${DURATION_SECONDS}s"
QUERIES_FILE="$(cd "$(dirname "$0")" && pwd)/queries.yaml"

if [[ ! -f "$QUERIES_FILE" ]]; then
  echo "Error: queries.yaml not found at $QUERIES_FILE" >&2
  exit 1
fi

### --- Tool checks ---
for cmd in yq jq curl; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "Error: '$cmd' not in PATH." >&2
    exit 1
  }
done

# EXTRA_OPTS is a free-form string. Word-split into an array, then expand with
# the `${arr[@]+"${arr[@]}"}` idiom so curl gets zero extra args when the
# array is empty (bash 3.2 + set -u would otherwise raise "unbound variable").
read -ra EXTRA_OPTS_ARR <<<"$EXTRA_OPTS"

### --- Run queries ---

count=$(yq '.queries | length' "$QUERIES_FILE")
declare -a json_entries=()

for i in $(seq 0 $((count - 1))); do
  name=$(yq  ".queries[${i}].name" "$QUERIES_FILE")
  query=$(yq ".queries[${i}].query" "$QUERIES_FILE")

  promql=${query//\$NAMESPACE/$NAMESPACE}
  promql=${promql//\$DURATION_S/$DURATION_S}

  resp=$(curl -sf -G ${EXTRA_OPTS_ARR[@]+"${EXTRA_OPTS_ARR[@]}"} \
        "${ENDPOINT}/api/v1/query" \
        --data-urlencode "query=$promql" 2>/dev/null) || continue

  [[ "$(jq -r '.status' <<<"$resp" 2>/dev/null || echo error)" == "success" ]] || continue

  value=$(jq -r '.data.result[0].value[1] // "null"' <<<"$resp")
  if [[ "$value" == "null" ]] || ! jq -en --argjson v "$value" 'true' >/dev/null 2>&1; then
    continue
  fi

  json_entries+=("$(jq -n --arg k "$name" --argjson v "$value" '{($k): $v}')")
done

### --- Output ---
if [[ ${#json_entries[@]} -eq 0 ]]; then
  echo '{}'
else
  printf '%s\n' "${json_entries[@]}" | jq -s 'add'
fi
