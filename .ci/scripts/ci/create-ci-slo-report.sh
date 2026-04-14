#!/bin/bash
set -euo pipefail

# SLO thresholds for health indicators
# Green: comfortably within SLO; yellow: approaching the SLO limit; red: SLO breached
P90_RUNTIME_GREEN=14    # minutes — healthy if at or below
P90_RUNTIME_YELLOW=15   # minutes — SLO limit; warning if at or below, critical if above
MQ_FAIL_RATE_GREEN=4    # percent — healthy if at or below
MQ_FAIL_RATE_YELLOW=5   # percent — SLO limit; warning if at or below, critical if above
RETRY_RATE_GREEN=4      # percent — healthy if at or below
RETRY_RATE_YELLOW=5     # percent — SLO limit; warning if at or below, critical if above

# Returns a Slack emoji health indicator based on value, thresholds, and direction
health_indicator() {
  local value="$1" green="$2" yellow="$3" direction="$4"
  if [ "$value" = "N/A" ]; then echo ":question:"; return; fi
  if [ "$direction" = "higher_is_better" ]; then
    awk -v v="$value" -v g="$green" -v y="$yellow" \
      'BEGIN { if (v+0 >= g+0) print ":green_circle:"; else if (v+0 >= y+0) print ":yellow_circle:"; else print ":orange_circle:" }'
  else
    awk -v v="$value" -v g="$green" -v y="$yellow" \
      'BEGIN { if (v+0 <= g+0) print ":green_circle:"; else if (v+0 <= y+0) print ":yellow_circle:"; else print ":orange_circle:" }'
  fi
}

# Returns ↑, ↓, or → to show week-over-week direction
trend_arrow() {
  local current="$1" previous="$2"
  if [ "$current" = "N/A" ] || [ "$previous" = "N/A" ]; then echo "→"; return; fi
  awk -v c="$current" -v p="$previous" \
    'BEGIN { if (c+0 > p+0) print "↑"; else if (c+0 < p+0) print "↓"; else print "→" }'
}

# Converts a SAFE_DIVIDE ratio (0–1) to a percentage string; passes through N/A
ratio_to_pct() {
  local val="$1"
  if [ "$val" = "N/A" ]; then echo "N/A"; return; fi
  awk -v v="$val" 'BEGIN { printf "%.1f", v * 100 }'
}

# --- KPI 1: P90 Unified CI runtime (minutes, SLO: ≤15 min) ---
# shellcheck disable=SC2016
CURRENT_P90_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'WITH a AS (
     SELECT MAX(report_time) AS latest_report_time, build_id
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
       AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND workflow_name="CI" AND build_status="success"
       AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
         OR (build_trigger="push" AND build_ref="refs/heads/main"))
     GROUP BY build_id
   ), b AS (
     SELECT build_id, MAX(build_duration_milliseconds) AS slowest_build_job_duration_milliseconds
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
       AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND workflow_name="CI" AND build_status="success"
       AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
         OR (build_trigger="push" AND build_ref="refs/heads/main"))
     GROUP BY build_id ORDER BY build_id ASC
   )
   SELECT ROUND(APPROX_QUANTILES(IEEE_DIVIDE(b.slowest_build_job_duration_milliseconds, 1000 * 60.0), 100)[OFFSET(90)], 1) AS p90
   FROM a FULL OUTER JOIN b ON a.build_id = b.build_id')
# shellcheck disable=SC2016
PREV_P90_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'WITH a AS (
     SELECT MAX(report_time) AS latest_report_time, build_id
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 14 DAY)<=report_time
       AND report_time<TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND workflow_name="CI" AND build_status="success"
       AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
         OR (build_trigger="push" AND build_ref="refs/heads/main"))
     GROUP BY build_id
   ), b AS (
     SELECT build_id, MAX(build_duration_milliseconds) AS slowest_build_job_duration_milliseconds
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 14 DAY)<=report_time
       AND report_time<TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND workflow_name="CI" AND build_status="success"
       AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
         OR (build_trigger="push" AND build_ref="refs/heads/main"))
     GROUP BY build_id ORDER BY build_id ASC
   )
   SELECT ROUND(APPROX_QUANTILES(IEEE_DIVIDE(b.slowest_build_job_duration_milliseconds, 1000 * 60.0), 100)[OFFSET(90)], 1) AS p90
   FROM a FULL OUTER JOIN b ON a.build_id = b.build_id')
CURRENT_P90=$(echo "$CURRENT_P90_JSON" | jq -r '.[0].p90 // "N/A"')
PREV_P90=$(echo "$PREV_P90_JSON" | jq -r '.[0].p90 // "N/A"')
P90_HEALTH=$(health_indicator "$CURRENT_P90" "$P90_RUNTIME_GREEN" "$P90_RUNTIME_YELLOW" "lower_is_better")
P90_TREND=$(trend_arrow "$CURRENT_P90" "$PREV_P90")

# --- Top 3 slowest CI jobs (P90 runtime, success only) ---
# shellcheck disable=SC2016
TOP3_SLOWEST_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'SELECT CONCAT(workflow_name, ": ", job_name) AS workflow_job,
     ROUND(APPROX_QUANTILES(IEEE_DIVIDE(build_duration_milliseconds, 1000 * 60.0), 100)[OFFSET(90)], 1) AS p90
   FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
   WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
     AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
     AND ci_url="https://github.com/camunda/camunda"
     AND workflow_name="CI"
     AND NOT STARTS_WITH(job_name, "deploy-")
     AND build_status="success"
     AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
       OR (build_trigger="push" AND build_ref="refs/heads/main"))
   GROUP BY workflow_job
   ORDER BY p90 DESC
   LIMIT 3')

# --- KPI 2: GHA merge queue failure rate (SLO: ≤5%) ---
# shellcheck disable=SC2016
CURRENT_MQ_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'WITH a AS (
     SELECT COUNT(DISTINCT SPLIT(build_id, "/")[0]) AS number_of_unsuccessful_workflow_runs
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
       AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND build_trigger="merge_group" AND build_base_ref="refs/heads/main"
       AND build_status!="success"
   ), b AS (
     SELECT COUNT(DISTINCT SPLIT(build_id, "/")[0]) AS number_of_all_workflow_runs
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
       AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND build_trigger="merge_group" AND build_base_ref="refs/heads/main"
   )
   SELECT SAFE_DIVIDE(a.number_of_unsuccessful_workflow_runs, b.number_of_all_workflow_runs) AS eviction_rate FROM a, b')
# shellcheck disable=SC2016
PREV_MQ_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'WITH a AS (
     SELECT COUNT(DISTINCT SPLIT(build_id, "/")[0]) AS number_of_unsuccessful_workflow_runs
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 14 DAY)<=report_time
       AND report_time<TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND build_trigger="merge_group" AND build_base_ref="refs/heads/main"
       AND build_status!="success"
   ), b AS (
     SELECT COUNT(DISTINCT SPLIT(build_id, "/")[0]) AS number_of_all_workflow_runs
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 14 DAY)<=report_time
       AND report_time<TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)
       AND ci_url="https://github.com/camunda/camunda"
       AND build_trigger="merge_group" AND build_base_ref="refs/heads/main"
   )
   SELECT SAFE_DIVIDE(a.number_of_unsuccessful_workflow_runs, b.number_of_all_workflow_runs) AS eviction_rate FROM a, b')
CURRENT_MQ_RATE=$(ratio_to_pct "$(echo "$CURRENT_MQ_JSON" | jq -r '.[0].eviction_rate // "N/A"')")
PREV_MQ_RATE=$(ratio_to_pct "$(echo "$PREV_MQ_JSON" | jq -r '.[0].eviction_rate // "N/A"')")
MQ_HEALTH=$(health_indicator "$CURRENT_MQ_RATE" "$MQ_FAIL_RATE_GREEN" "$MQ_FAIL_RATE_YELLOW" "lower_is_better")
MQ_TREND=$(trend_arrow "$CURRENT_MQ_RATE" "$PREV_MQ_RATE")

# --- Top 3 failing CI jobs in the merge queue ---
# shellcheck disable=SC2016
TOP3_MQ_FAILING_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'SELECT CONCAT(workflow_name, ": ", job_name) AS workflow_job,
     SAFE_DIVIDE(COUNTIF(build_status != "success"), COUNT(*)) AS failure_rate,
     COUNTIF(build_status != "success") AS caused_failures
   FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
   WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
     AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
     AND ci_url="https://github.com/camunda/camunda"
     AND build_trigger="merge_group" AND build_base_ref="refs/heads/main"
   GROUP BY workflow_job
   ORDER BY failure_rate DESC
   LIMIT 3')

# --- KPI 3: GHA workflow run retry rate (SLO: ≤5%) ---
# shellcheck disable=SC2016
CURRENT_RETRY_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'WITH a AS (
     SELECT COUNT(*) AS workflow_run_retries FROM (
       SELECT workflow_name, SPLIT(build_id, "/")[0] AS workflow_run_id, MAX(SPLIT(build_id, "/")[1]) AS max_build_attempt
       FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
       WHERE ci_url="https://github.com/camunda/camunda"
         AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
           OR (build_trigger="push" AND build_ref="refs/heads/main"))
         AND TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
         AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
       GROUP BY workflow_run_id, workflow_name
     )
     WHERE max_build_attempt != "1"
   ), b AS (
     SELECT COUNT(DISTINCT SPLIT(build_id, "/")[0]) AS number_of_all_workflow_runs
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE ci_url="https://github.com/camunda/camunda"
       AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
         OR (build_trigger="push" AND build_ref="refs/heads/main"))
       AND TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=report_time
       AND report_time<TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
   )
   SELECT SAFE_DIVIDE(a.workflow_run_retries, b.number_of_all_workflow_runs) AS retry_rate FROM a, b')
# shellcheck disable=SC2016
PREV_RETRY_JSON=$(bq query --format=json --use_legacy_sql=false --quiet=true \
  'WITH a AS (
     SELECT COUNT(*) AS workflow_run_retries FROM (
       SELECT workflow_name, SPLIT(build_id, "/")[0] AS workflow_run_id, MAX(SPLIT(build_id, "/")[1]) AS max_build_attempt
       FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
       WHERE ci_url="https://github.com/camunda/camunda"
         AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
           OR (build_trigger="push" AND build_ref="refs/heads/main"))
         AND TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 14 DAY)<=report_time
         AND report_time<TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)
       GROUP BY workflow_run_id, workflow_name
     )
     WHERE max_build_attempt != "1"
   ), b AS (
     SELECT COUNT(DISTINCT SPLIT(build_id, "/")[0]) AS number_of_all_workflow_runs
     FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
     WHERE ci_url="https://github.com/camunda/camunda"
       AND (((build_trigger="merge_group" OR build_trigger="pull_request") AND build_base_ref="refs/heads/main")
         OR (build_trigger="push" AND build_ref="refs/heads/main"))
       AND TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 14 DAY)<=report_time
       AND report_time<TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)
   )
   SELECT SAFE_DIVIDE(a.workflow_run_retries, b.number_of_all_workflow_runs) AS retry_rate FROM a, b')
CURRENT_RETRY_RATE=$(ratio_to_pct "$(echo "$CURRENT_RETRY_JSON" | jq -r '.[0].retry_rate // "N/A"')")
PREV_RETRY_RATE=$(ratio_to_pct "$(echo "$PREV_RETRY_JSON" | jq -r '.[0].retry_rate // "N/A"')")
RETRY_HEALTH=$(health_indicator "$CURRENT_RETRY_RATE" "$RETRY_RATE_GREEN" "$RETRY_RATE_YELLOW" "lower_is_better")
RETRY_TREND=$(trend_arrow "$CURRENT_RETRY_RATE" "$PREV_RETRY_RATE")

{
  echo "MESSAGE<<EOF"
  echo ":bar_chart: *Weekly Monorepo CI SLO Report* for \`main\` (last 7 days vs. prior 7 days)"
  echo ""
  echo "${P90_HEALTH} *P90 Unified CI runtime:* ${CURRENT_P90} min ${P90_TREND} (prev: ${PREV_P90} min) | SLO: ≤${P90_RUNTIME_YELLOW} min"
  echo "$TOP3_SLOWEST_JSON" | jq -r '.[] | "  • \(.workflow_job) (p90: \(.p90) min)"'
  echo ""
  echo "${MQ_HEALTH} *Merge queue failure rate:* ${CURRENT_MQ_RATE}% ${MQ_TREND} (prev: ${PREV_MQ_RATE}%) | SLO: ≤${MQ_FAIL_RATE_YELLOW}%"
  echo "$TOP3_MQ_FAILING_JSON" | jq -r '.[] | "  • \(.workflow_job) (\( .failure_rate | tonumber * 100 | (. * 10 | round) / 10)% failure rate, \(.caused_failures) failures)"'
  echo ""
  echo "${RETRY_HEALTH} *Workflow run retry rate:* ${CURRENT_RETRY_RATE}% ${RETRY_TREND} (prev: ${PREV_RETRY_RATE}%) | SLO: ≤${RETRY_RATE_YELLOW}%"
  echo ""
  echo "📊 Full details: <https://dashboard.int.camunda.com/d/5cd87b35-d2f2-4686-b3ab-2ef4de504364/ci-health-c8-monorepo?orgId=1&from=now-7d%2Fd&to=now-1d%2Fd|Grafana SLO Dashboard>"
  echo "EOF"
} >> "$GITHUB_OUTPUT"
