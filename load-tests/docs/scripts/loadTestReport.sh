#!/usr/bin/env bash
# Build a wide load-test report from Prometheus and emit JSON, CSV, or TSV.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: loadTestReport.sh <namespace> [options]

Arguments:
  namespace                 Exact load-test namespace, e.g. c8-ck-baseline-20260814.

Options:
  --duration-seconds <sec>  Query window duration. Default: 600.
  --rate-interval <dur>     Short rate interval for dashboard-style rollups. Default: 5m.
  --sample-step <dur>       Subquery sample resolution for window summaries. Default: 1m.
  --template <name>         Report template: camunda or zeebe-gateway. Default: camunda.
  --at <time>               Prometheus query time anchor, RFC3339 or Unix timestamp.
                            Queries cover (--duration-seconds) ending at this time.
  --start <time>            Start of the reporting window, RFC3339 or Unix timestamp.
  --end <time>              End of the reporting window, RFC3339 or Unix timestamp.
                            When --start/--end are set, duration is derived from them.
  --endpoint <url>          Prometheus base URL. Default: http://localhost:9090.
  --curl-opts <opts>        Extra curl options string, e.g. '--user "u:p"'.
  --format <format>         Output format: json, csv, or tsv. Default: json.
  --no-header               Omit the CSV/TSV header row.
  --missing-value <value>   CSV/TSV placeholder for missing metrics. Default: NaN.
  --queries-file <path>     Query definition file. Overrides --template.
  --output <path>           Write output to a file instead of stdout.
  -h, --help                Show this help message.

Examples:
  # Port-forwarded Prometheus, JSON:
  ./loadTestReport.sh c8-ck-baseline-20260814 --duration-seconds 1800

  # Historical window, spreadsheet-friendly TSV:
  ./loadTestReport.sh c8-ck-baseline-20260814 \
    --start 2026-08-14T10:00:00Z \
    --end 2026-08-14T10:30:00Z \
    --format tsv --no-header

  # CI monitor ingress with basic auth:
  ./loadTestReport.sh c8-ck-baseline-20260814 \
    --duration-seconds 1800 \
    --endpoint https://ci-monitor.benchmark.camunda.cloud \
    --curl-opts "--user $PROM_USER:$PROM_PASS" \
    --format csv > /tmp/load-test-report.csv
EOF
}

die() {
  echo "Error: $*" >&2
  exit 1
}

prometheus_endpoint_help() {
  cat <<EOF
Could not reach Prometheus endpoint '$ENDPOINT'.

If you are running locally, start the port-forward in another terminal:

  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090

Then rerun this script with:

  --endpoint http://localhost:9090

If you are using the CI monitor ingress, verify the URL and pass credentials with
--curl-opts, for example:

  --endpoint https://ci-monitor.benchmark.camunda.cloud --curl-opts "--user \$PROM_USER:\$PROM_PASS"
EOF
}

parse_epoch() {
  local value="$1"

  if [[ "$value" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "$value"
    return
  fi

  date -u -d "$value" +%s 2>/dev/null || return 1
}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE=""
DURATION_SECONDS="600"
RATE_INTERVAL="5m"
SAMPLE_STEP="1m"
REPORT_TEMPLATE="camunda"
ENDPOINT="http://localhost:9090"
EXTRA_OPTS=""
TIME_ANCHOR=""
START_TIME=""
END_TIME=""
FORMAT="json"
INCLUDE_HEADER="true"
MISSING_VALUE="NaN"
QUERIES_FILE=""
OUTPUT_FILE=""

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -gt 0 && "${1:-}" != --* ]]; then
  NAMESPACE="$1"
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)
      [[ $# -ge 2 ]] || die "Missing value for --namespace."
      NAMESPACE="$2"
      shift 2
      ;;
    --duration-seconds)
      [[ $# -ge 2 ]] || die "Missing value for --duration-seconds."
      DURATION_SECONDS="$2"
      shift 2
      ;;
    --rate-interval)
      [[ $# -ge 2 ]] || die "Missing value for --rate-interval."
      RATE_INTERVAL="$2"
      shift 2
      ;;
    --sample-step)
      [[ $# -ge 2 ]] || die "Missing value for --sample-step."
      SAMPLE_STEP="$2"
      shift 2
      ;;
    --template)
      [[ $# -ge 2 ]] || die "Missing value for --template."
      REPORT_TEMPLATE="$2"
      shift 2
      ;;
    --at)
      [[ $# -ge 2 ]] || die "Missing value for --at."
      TIME_ANCHOR="$2"
      shift 2
      ;;
    --start)
      [[ $# -ge 2 ]] || die "Missing value for --start."
      START_TIME="$2"
      shift 2
      ;;
    --end)
      [[ $# -ge 2 ]] || die "Missing value for --end."
      END_TIME="$2"
      shift 2
      ;;
    --endpoint)
      [[ $# -ge 2 ]] || die "Missing value for --endpoint."
      ENDPOINT="$2"
      shift 2
      ;;
    --curl-opts)
      [[ $# -ge 2 ]] || die "Missing value for --curl-opts."
      EXTRA_OPTS="$2"
      shift 2
      ;;
    --format)
      [[ $# -ge 2 ]] || die "Missing value for --format."
      FORMAT="$2"
      shift 2
      ;;
    --no-header)
      INCLUDE_HEADER="false"
      shift
      ;;
    --missing-value)
      [[ $# -ge 2 ]] || die "Missing value for --missing-value."
      MISSING_VALUE="$2"
      shift 2
      ;;
    --queries-file)
      [[ $# -ge 2 ]] || die "Missing value for --queries-file."
      QUERIES_FILE="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || die "Missing value for --output."
      OUTPUT_FILE="$2"
      shift 2
      ;;
    *)
      die "Unknown argument '$1'. Run with --help for usage."
      ;;
  esac
done

[[ -n "$NAMESPACE" ]] || die "Missing <namespace>."

if (( ${#NAMESPACE} > 63 )) || ! [[ "$NAMESPACE" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
  die "namespace '$NAMESPACE' must be a valid Kubernetes DNS label (max 63 characters; lowercase alphanumeric or '-', and must start and end with an alphanumeric character)."
fi

if ! [[ "$DURATION_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  die "duration-seconds '$DURATION_SECONDS' must be a positive integer."
fi

if ! [[ "$RATE_INTERVAL" =~ ^[1-9][0-9]*(ms|s|m|h|d|w|y)$ ]]; then
  die "rate-interval '$RATE_INTERVAL' must be a Prometheus duration like 30s, 5m, or 1h."
fi

if ! [[ "$SAMPLE_STEP" =~ ^[1-9][0-9]*(ms|s|m|h|d|w|y)$ ]]; then
  die "sample-step '$SAMPLE_STEP' must be a Prometheus duration like 30s, 1m, or 5m."
fi

if [[ -n "$START_TIME" || -n "$END_TIME" ]]; then
  [[ -n "$START_TIME" && -n "$END_TIME" ]] || die "--start and --end must be provided together."
  [[ -z "$TIME_ANCHOR" ]] || die "--at cannot be combined with --start/--end."

  START_EPOCH="$(parse_epoch "$START_TIME")" || die "Could not parse --start '$START_TIME'."
  END_EPOCH="$(parse_epoch "$END_TIME")" || die "Could not parse --end '$END_TIME'."
  (( END_EPOCH > START_EPOCH )) || die "--end must be after --start."

  DURATION_SECONDS="$((END_EPOCH - START_EPOCH))"
  TIME_ANCHOR="$END_TIME"
fi

case "$FORMAT" in
  json|csv|tsv) ;;
  *) die "Unsupported --format '$FORMAT'. Expected json, csv, or tsv." ;;
esac

if [[ -z "$QUERIES_FILE" ]]; then
  case "$REPORT_TEMPLATE" in
    camunda)
      QUERIES_FILE="$SCRIPT_DIR/report-queries.json"
      ;;
    zeebe-gateway)
      QUERIES_FILE="$SCRIPT_DIR/report-queries-zeebe-gateway.json"
      ;;
    *)
      die "Unsupported --template '$REPORT_TEMPLATE'. Expected camunda or zeebe-gateway."
      ;;
  esac
fi

[[ -f "$QUERIES_FILE" ]] || die "queries file not found at $QUERIES_FILE."

for cmd in jq curl; do
  command -v "$cmd" >/dev/null 2>&1 || die "'$cmd' not in PATH."
done

DURATION_S="${DURATION_SECONDS}s"
RATE_INTERVAL_S="$RATE_INTERVAL"
SAMPLE_STEP_S="$SAMPLE_STEP"
GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
START_LABEL=""
END_LABEL=""

if [[ -n "$TIME_ANCHOR" ]]; then
  anchor_epoch="$(parse_epoch "$TIME_ANCHOR")" || die "Could not parse --at '$TIME_ANCHOR'."
  START_LABEL="$(date -u -d "@$((anchor_epoch - DURATION_SECONDS))" +"%Y-%m-%dT%H:%M:%SZ")"
  END_LABEL="$(date -u -d "@$anchor_epoch" +"%Y-%m-%dT%H:%M:%SZ")"
fi

read -ra EXTRA_OPTS_ARR <<<"$EXTRA_OPTS"

declare -a TIME_ARGS=()
if [[ -n "$TIME_ANCHOR" ]]; then
  TIME_ARGS=(--data-urlencode "time=$TIME_ANCHOR")
fi

if ! endpoint_resp="$(curl -sf --connect-timeout 5 --max-time 15 \
    ${EXTRA_OPTS_ARR[@]+"${EXTRA_OPTS_ARR[@]}"} \
    "${ENDPOINT}/api/v1/status/runtimeinfo" 2>/dev/null)"; then
  die "$(prometheus_endpoint_help)"
fi

if [[ "$(jq -r '.status' <<<"$endpoint_resp" 2>/dev/null || echo error)" != "success" ]]; then
  die "$(cat <<EOF
Endpoint '$ENDPOINT' is reachable, but it did not return a Prometheus API success response.

If you are running locally, make sure the port-forward points at Prometheus:

  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
EOF
)"
fi

count="$(jq '.queries | length' "$QUERIES_FILE")"
declare -a key_entries=()
declare -a header_entries=()
declare -a metric_entries=()
declare -a missing_entries=()

for i in $(seq 0 $((count - 1))); do
  key="$(jq -r ".queries[$i].key" "$QUERIES_FILE")"
  header="$(jq -r ".queries[$i].header // .queries[$i].key" "$QUERIES_FILE")"
  query="$(jq -r ".queries[$i].query // empty" "$QUERIES_FILE")"
  label="$(jq -r ".queries[$i].valueLabel // empty" "$QUERIES_FILE")"
  static_value="$(jq -r ".queries[$i].value // empty" "$QUERIES_FILE")"

  value_json="null"

  if [[ -n "$static_value" ]]; then
    static_value="${static_value//\$NAMESPACE/$NAMESPACE}"
    value_json="$(jq -n --arg v "$static_value" '$v')"
  else
    promql="${query//\$NAMESPACE/$NAMESPACE}"
    promql="${promql//\$DURATION_S/$DURATION_S}"
    promql="${promql//\$RATE_INTERVAL/$RATE_INTERVAL_S}"
    promql="${promql//\$SAMPLE_STEP/$SAMPLE_STEP_S}"

    if resp="$(curl -sf -G ${EXTRA_OPTS_ARR[@]+"${EXTRA_OPTS_ARR[@]}"} \
        "${ENDPOINT}/api/v1/query" \
        --data-urlencode "query=$promql" \
        ${TIME_ARGS[@]+"${TIME_ARGS[@]}"} 2>/dev/null)"; then
      if [[ "$(jq -r '.status' <<<"$resp" 2>/dev/null || echo error)" == "success" ]]; then
        if [[ -n "$label" ]]; then
          raw_value="$(jq -r --arg label "$label" '[.data.result[]?.metric[$label] // empty] | unique | join(", ")' <<<"$resp")"
          if [[ -n "$raw_value" ]]; then
            value_json="$(jq -n --arg v "$raw_value" '$v')"
          else
            missing_entries+=("$(jq -n --arg key "$key" --arg reason "no label sample" '{key: $key, reason: $reason}')")
          fi
        else
          raw_value="$(jq -r '.data.result[0].value[1] // empty' <<<"$resp")"
          if [[ "$raw_value" =~ ^-?([0-9]+([.][0-9]+)?|[.][0-9]+)([eE][-+]?[0-9]+)?$ ]]; then
            value_json="$raw_value"
          else
            missing_entries+=("$(jq -n --arg key "$key" --arg reason "no numeric sample" '{key: $key, reason: $reason}')")
          fi
        fi
      else
        missing_entries+=("$(jq -n --arg key "$key" --arg reason "Prometheus returned non-success status" '{key: $key, reason: $reason}')")
      fi
    else
      missing_entries+=("$(jq -n --arg key "$key" --arg reason "query failed" '{key: $key, reason: $reason}')")
    fi
  fi

  key_entries+=("$(jq -n --arg v "$key" '$v')")
  header_entries+=("$(jq -n --arg v "$header" '$v')")
  metric_entries+=("$(jq -n --arg k "$key" --argjson v "$value_json" '{($k): $v}')")
done

keys_json="$(printf '%s\n' "${key_entries[@]}" | jq -s '.')"
headers_json="$(printf '%s\n' "${header_entries[@]}" | jq -s '.')"
metrics_json="$(printf '%s\n' "${metric_entries[@]}" | jq -s 'add')"
missing_json="[]"
if [[ ${#missing_entries[@]} -gt 0 ]]; then
  missing_json="$(printf '%s\n' "${missing_entries[@]}" | jq -s '.')"
fi

report_json="$(jq -n \
  --arg namespace "$NAMESPACE" \
  --arg durationSeconds "$DURATION_SECONDS" \
  --arg endpoint "$ENDPOINT" \
  --arg generatedAt "$GENERATED_AT" \
  --arg start "$START_LABEL" \
  --arg end "$END_LABEL" \
  --argjson keys "$keys_json" \
  --argjson headers "$headers_json" \
  --argjson metrics "$metrics_json" \
  --argjson missing "$missing_json" \
  '{
    namespace: $namespace,
    durationSeconds: ($durationSeconds | tonumber),
    start: (if $start == "" then null else $start end),
    end: (if $end == "" then null else $end end),
    endpoint: $endpoint,
    generatedAt: $generatedAt,
    columns: $keys,
    headers: $headers,
    metrics: $metrics,
    missing: $missing
  }')"

rendered="$(
  jq -r \
    --arg format "$FORMAT" \
    --arg missingValue "$MISSING_VALUE" \
    --argjson includeHeader "$INCLUDE_HEADER" \
    --argjson report "$report_json" '
      def row($values):
        if $format == "csv" then $values | @csv else $values | @tsv end;

      if $format == "json" then
        $report
      else
        ($report.headers) as $headers |
        ($report.columns | map(if $report.metrics[.] == null then $missingValue else $report.metrics[.] end)) as $values |
        if $includeHeader then
          row($headers), row($values)
        else
          row($values)
        end
      end
    ' <<< '{}'
)"

if [[ -n "$OUTPUT_FILE" ]]; then
  printf '%s\n' "$rendered" > "$OUTPUT_FILE"
else
  printf '%s\n' "$rendered"
fi
