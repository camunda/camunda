#!/usr/bin/env bash
# Runs every query behind a dashboard row against Prometheus and reports how many
# series each panel would draw.
#
# A panel that renders empty looks the same whether nothing happened, the metric
# is not published, or the query is wrong. This answers which, before or after a
# run rather than by squinting at a dashboard.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

NS=""
ROW="Rebalancing"
DASHBOARD="$HERE/../../monitor/grafana/zeebe.json"
PROM_URL="http://localhost:9090"
AT=""
RANGE="30m"
RATE_INTERVAL="2m"

usage() {
  cat <<'EOF'
Usage: check-panels.sh --namespace <ns> [options]

Options:
  -n, --namespace <ns>   Namespace to substitute for $namespace (required)
      --row <title>      Dashboard row to check (default Rebalancing)
      --dashboard <path> Dashboard JSON (default ../../monitor/grafana/zeebe.json)
      --prom <url>       Prometheus base URL (default http://localhost:9090, i.e. a port forward
                         to monitoring/kube-prometheus-stack-prometheus)
      --at <time>        Evaluate as of this instant (RFC3339 or Unix seconds) instead of now,
                         to check a window a run has already finished
      --range <dur>      Substituted for $__range (default 30m)
      --rate-interval <dur> Substituted for $__rate_interval and $__interval (default 2m)
  -h, --help             Show this help

Exits non-zero when any panel would draw nothing.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--namespace) NS=$2; shift 2 ;;
    --row) ROW=$2; shift 2 ;;
    --dashboard) DASHBOARD=$2; shift 2 ;;
    --prom) PROM_URL=$2; shift 2 ;;
    --at) AT=$2; shift 2 ;;
    --range) RANGE=$2; shift 2 ;;
    --rate-interval) RATE_INTERVAL=$2; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

[[ -z "$NS" ]] && { echo "--namespace is required" >&2; usage >&2; exit 1; }
[[ -f "$DASHBOARD" ]] || { echo "dashboard not found: $DASHBOARD" >&2; exit 1; }

declare -a TIME_ARGS=()
[[ -n "$AT" ]] && TIME_ARGS=(--data-urlencode "time=$AT")

empty=0
total=0

# A collapsed row holds its panels; an expanded one is followed by them. Both
# shapes are read, so this works whichever way the row was last saved.
panels=$(jq -c --arg row "$ROW" '
  (.panels | to_entries) as $entries
  | ($entries | map(select(.value.type == "row" and .value.title == $row)) | first) as $rowEntry
  | if $rowEntry == null then error("row not found: " + $row)
    elif (($rowEntry.value.panels // []) | length) > 0 then $rowEntry.value.panels
    else [ $entries[] | select(.key > $rowEntry.key)
           | .value ] | (. as $after
             | ($after | map(.type == "row") | index(true)) as $next
             | if $next == null then $after else $after[0:$next] end)
    end
  | .[] | {title, targets: [(.targets // [])[] | .expr | select(. != null)]}
' "$DASHBOARD")

while read -r panel; do
  title=$(jq -r '.title // "<untitled>"' <<<"$panel")
  while read -r expr; do
    [[ -z "$expr" || "$expr" == "null" ]] && continue
    promql=$expr
    promql=${promql//\$cluster/.*}
    promql=${promql//\$namespace/$NS}
    promql=${promql//\$physicalTenant/.*}
    promql=${promql//\$partition/.*}
    promql=${promql//\$pod/.*}
    promql=${promql//\$__rate_interval/$RATE_INTERVAL}
    promql=${promql//\$__interval/$RATE_INTERVAL}
    promql=${promql//\$__range/$RANGE}

    total=$((total + 1))
    response=$(curl -sS -m 25 -G "$PROM_URL/api/v1/query" \
      --data-urlencode "query=$promql" ${TIME_ARGS[@]+"${TIME_ARGS[@]}"} 2>/dev/null)
    if [[ -z "$response" ]] || [[ "$(jq -r '.status' <<<"$response" 2>/dev/null)" != "success" ]]; then
      printf '  %-12s %s\n' "ERROR" "$title"
      printf '               %s\n' "$(jq -r '.error // "no answer from Prometheus"' <<<"${response:-{\}}" 2>/dev/null)"
      empty=$((empty + 1))
      continue
    fi
    series=$(jq '.data.result | length' <<<"$response")
    if [[ "$series" -eq 0 ]]; then
      printf '  %-12s %s\n' "EMPTY" "$title"
      printf '               %s\n' "$promql"
      empty=$((empty + 1))
    else
      printf '  %-12s %s\n' "$series series" "$title"
    fi
  done < <(jq -r '.targets[]' <<<"$panel")
done <<<"$panels"

echo
if [[ "$empty" -gt 0 ]]; then
  echo "$empty of $total queries in row '$ROW' would draw nothing for $NS."
  exit 1
fi
echo "All $total queries in row '$ROW' have data for $NS."
