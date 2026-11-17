#!/usr/bin/env bash
# Suite-health check for the scheduled Orchestration Cluster nightlies, ported from
# scripts/triage-suite-health.sh in camunda/controller-e2e-test-suite.
#
# Triage reads the latest run of each nightly; this reads its scheduled history. A nightly is
# unhealthy when its last STREAK_THRESHOLD scheduled runs on main are all red, or when it has had
# no scheduled run for MISSING_HOURS (for example because the workflow was disabled).
#
# Each condition is announced once, again every REALERT_DAYS while it lasts, and once more when it
# clears. Cancelled and skipped runs neither count nor break a streak. Weekend runs count, although
# triage itself does not run on weekends.
#
# Required env:
#   GH_TOKEN
#   NIGHTLY_WORKFLOWS  one "workflow_file|version|test_type" per line, the triage scan scope.
#                      test_type is e2e, api_es or api_rdbms. Blank lines and "#" lines are ignored.
#
# Optional env:
#   REPO                 (default: camunda/camunda)
#   STATE_FILE           state carried between days (default: health.json)
#   ALERTS_FILE          one Slack line per alert, empty when there is none (default: health-alerts.txt)
#   STREAK_THRESHOLD     (default: 3)
#   MISSING_HOURS        (default: 26)
#   REALERT_DAYS         (default: 7)
#   NOW_EPOCH            current time in seconds (default: now)
#   RETRY_DELAY_SECONDS  base delay between gh retries (default: 10)

set -euo pipefail

REPO="${REPO:-camunda/camunda}"
STATE_FILE="${STATE_FILE:-health.json}"
ALERTS_FILE="${ALERTS_FILE:-health-alerts.txt}"
STREAK_THRESHOLD="${STREAK_THRESHOLD:-3}"
MISSING_HOURS="${MISSING_HOURS:-26}"
REALERT_DAYS="${REALERT_DAYS:-7}"
NOW_EPOCH="${NOW_EPOCH:-$(date -u +%s)}"
RETRY_DELAY_SECONDS="${RETRY_DELAY_SECONDS:-10}"
REALERT_SECONDS=$((REALERT_DAYS * 86400))

mapfile -t WORKFLOWS < <(printf '%s\n' "${NIGHTLY_WORKFLOWS:-}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' | grep -v -e '^$' -e '^#' || true)
if [ "${#WORKFLOWS[@]}" -eq 0 ]; then
  echo "::error::NIGHTLY_WORKFLOWS is empty; nothing to check" >&2
  exit 1
fi

mkdir -p "$(dirname "$ALERTS_FILE")"
: > "$ALERTS_FILE"
[ -s "$STATE_FILE" ] || echo '{}' > "$STATE_FILE"
if ! state=$(jq -c 'if type == "object" then . else error("not an object") end' "$STATE_FILE" 2>/dev/null); then
  # Losing the state re-announces open conditions once; failing here would stop every alert.
  echo "::warning::${STATE_FILE} is not a JSON object; starting from an empty state" >&2
  state='{}'
fi

list_runs() {
  local wf="$1" attempt out
  for attempt in 1 2 3; do
    if out=$(gh run list --repo "$REPO" --workflow "$wf" --event schedule \
        --branch main --limit 30 \
        --json databaseId,status,conclusion,createdAt,url); then
      printf '%s' "$out"
      return 0
    fi
    sleep $((attempt * RETRY_DELAY_SECONDS))
  done
  # An unverifiable history must not read as healthy, nor clear an open alert.
  echo "::error::gh run list failed for ${wf}; suite health not checked" >&2
  return 1
}

label_of() {
  local version="$1" test_type="$2" display
  case "$test_type" in
    e2e)       display="E2E" ;;
    api_es)    display="API (ES)" ;;
    api_rdbms) display="API (RDBMS)" ;;
    *)         display="$test_type" ;;
  esac
  printf 'Nightly %s -- %s' "$display" "$version"
}

day() { date -u -d "$1" +%F; }

scope_keys='[]'
for spec in "${WORKFLOWS[@]}"; do
  IFS='|' read -r wf version test_type <<<"$spec"
  label=$(label_of "$version" "$test_type")
  scope_keys=$(jq -c --arg k "$wf" '. + [$k]' <<<"$scope_keys")
  runs=$(list_runs "$wf")

  # Leading run of red scheduled runs, newest first, ignoring runs that were cancelled, skipped or
  # are still running. "capped" means the streak reaches past the oldest listed run, so its start
  # is not visible.
  streak_json=$(jq -c '
    [ .[] | select(.status == "completed")
          | select(.conclusion | IN("cancelled", "skipped", "neutral", "stale") | not) ] as $done
    | ([ $done[] | .conclusion | IN("failure", "timed_out", "startup_failure") ] | index(false)) as $i
    | ($i // ($done | length)) as $n
    | { count: $n,
        capped: ($n > 0 and $n == ($done | length)),
        oldest: (if $n > 0 then $done[$n - 1] else null end),
        newest: (if $n > 0 then $done[0] else null end) }' <<<"$runs")
  count=$(jq -r '.count' <<<"$streak_json")
  capped=$(jq -r '.capped' <<<"$streak_json")
  alerted=$(jq -r --arg k "$wf" '.[$k].streak.start_run // empty' <<<"$state")
  alerted_day=$(jq -r --arg k "$wf" '.[$k].streak.start_day // empty' <<<"$state")
  alerted_at=$(jq -r --arg k "$wf" '.[$k].streak.alerted_at // 0' <<<"$state")

  if [ "$count" -ge "$STREAK_THRESHOLD" ]; then
    start_run=$(jq -r '.oldest.databaseId | tostring' <<<"$streak_json")
    start_day=$(day "$(jq -r '.oldest.createdAt' <<<"$streak_json")")
    newest_url=$(jq -r '.newest.url' <<<"$streak_json")
    runs_text="${count}"
    if [ "$capped" = "true" ]; then
      runs_text="at least ${count}"
      # A streak longer than the listed history loses its oldest run every day; keep the one
      # already announced instead of reading that as a new streak.
      if [ -n "$alerted" ]; then
        start_run="$alerted"
        start_day="${alerted_day:-$start_day}"
      fi
    fi
    if [ "$alerted" != "$start_run" ]; then
      echo ":rotating_light: *${label}* has been red on ${runs_text} consecutive scheduled runs, since ${start_day}. <${newest_url}|Latest run>" >> "$ALERTS_FILE"
      alerted_at="$NOW_EPOCH"
    elif [ $((NOW_EPOCH - alerted_at)) -ge "$REALERT_SECONDS" ]; then
      echo ":rotating_light: *${label}* is still red: ${runs_text} consecutive scheduled runs, since ${start_day}. <${newest_url}|Latest run>" >> "$ALERTS_FILE"
      alerted_at="$NOW_EPOCH"
    fi
    state=$(jq -c --arg k "$wf" --arg r "$start_run" --arg d "$start_day" --argjson t "$alerted_at" \
      '.[$k].streak = {start_run: $r, start_day: $d, alerted_at: $t}' <<<"$state")
  elif [ -n "$alerted" ]; then
    echo ":white_check_mark: *${label}*: the red streak announced earlier has ended." >> "$ALERTS_FILE"
    state=$(jq -c --arg k "$wf" 'del(.[$k].streak)' <<<"$state")
  fi

  # No scheduled run at all within MISSING_HOURS.
  latest_at=$(jq -r '.[0].createdAt // empty' <<<"$runs")
  missing_since=$(jq -r --arg k "$wf" '.[$k].missing.alerted_at // empty' <<<"$state")
  if [ -z "$latest_at" ] || [ $((NOW_EPOCH - $(date -u -d "$latest_at" +%s))) -ge $((MISSING_HOURS * 3600)) ]; then
    last="${latest_at:+$(day "$latest_at")}"
    if [ -z "$missing_since" ] || [ $((NOW_EPOCH - missing_since)) -ge "$REALERT_SECONDS" ]; then
      echo ":warning: *${label}* has had no scheduled run for more than ${MISSING_HOURS} hours (last: ${last:-never}). Check that \`${wf}\` is enabled." >> "$ALERTS_FILE"
      state=$(jq -c --arg k "$wf" --argjson t "$NOW_EPOCH" '.[$k].missing = {alerted_at: $t}' <<<"$state")
    fi
  elif [ -n "$missing_since" ]; then
    echo ":white_check_mark: *${label}* has scheduled runs again." >> "$ALERTS_FILE"
    state=$(jq -c --arg k "$wf" 'del(.[$k].missing)' <<<"$state")
  fi
done

# Drop the state of nightlies that left the scope, and of entries left empty.
jq --argjson keys "$scope_keys" \
  'with_entries(select(.key as $k | $keys | index($k)) | select(.value != {}))' <<<"$state" > "$STATE_FILE"
echo "Suite health: $(wc -l < "$ALERTS_FILE" | tr -d ' ') alert line(s) for ${#WORKFLOWS[@]} nightlies"
