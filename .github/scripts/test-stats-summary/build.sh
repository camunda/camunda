#!/usr/bin/env bash
# Builds the Slack summary of the tests that flaked (daily) or failed (weekly) most often in
# camunda/camunda CI, from CI Analytics in BigQuery. Prints the mrkdwn message to stdout.
#
# Usage: build.sh flaky|failure
#
#   flaky    previous day, all branches, medic ping at FLAKY_TEST_THRESHOLD flakes
#   failure  previous 7 days, main only, medic ping at FAILED_TEST_THRESHOLD failures
#
# A medic is pinged only when no GH issue matches the test.
#
# Run from the repo root: the medic lookup maps a test class to its source file with git.
# Needs bq (authenticated), gh (GH_TOKEN), codeowners-cli and jq on PATH.

set -euo pipefail

MODE="${1:-}"
case "$MODE" in
  flaky | failure) ;;
  *)
    echo "usage: $0 flaky|failure" >&2
    exit 2
    ;;
esac

FLAKY_TEST_THRESHOLD=10
FAILED_TEST_THRESHOLD=3
GRAFANA_URL="https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo"

# shellcheck source=.ci/scripts/ci/setup-medic-lookup.sh
source "$(dirname "${BASH_SOURCE[0]}")/../../../.ci/scripts/ci/setup-medic-lookup.sh"

bq_query() {
  bq query --format=json --use_legacy_sql=false --quiet=true "$1"
}

# Prints the first open or closed GH issue number for the search, or nothing.
find_issue() {
  gh issue list -R camunda/camunda --search "$1" --limit 1 --json number --jq '.[0].number // empty' \
    || { echo "::warning::gh issue lookup failed for $2" >&2; }
}

# Prints the medic of the team that owns the test class, or nothing.
find_medic() {
  local source_file owner
  source_file="$(resolve_test_source_file "$1")"
  [[ -n "$source_file" ]] || return 0
  owner="$(resolve_codeowners_team "$source_file")"
  if [[ -n "$owner" && -n "${lookupTeamMedic["$owner"]+x}" ]]; then
    echo "${lookupTeamMedic["$owner"]}"
  fi
}

# Prints the bullet for one test, its GH issue link, and the medic ping when it is due.
# Args: count, fully qualified class name (or spec path), test name, medic threshold.
print_test() {
  local count="$1" fqcn="$2" test_name="$3" threshold="$4"
  local class_name issue_filter="" fallback_search issue_id suffix="" medic

  [[ "$MODE" == "flaky" ]] && issue_filter=" AND (label:kind/flake OR in:title flaky)"

  if [[ "$fqcn" == *.ts || "$fqcn" == *.tsx ]]; then
    # Spec path (e.g. src/foo/Bar.test.tsx) -> Bar, as flaky-test issue titles name it
    class_name="${fqcn##*/}"
    class_name="${class_name%%.*}"
    # Spec names like Processes or login are too generic to match on their own
    if [[ "$MODE" == "flaky" ]]; then
      fallback_search="in:title \"$class_name\"$issue_filter"
    else
      fallback_search="in:title flaky \"$class_name\""
    fi
  else
    class_name="${fqcn##*.}"
    fallback_search="in:title $class_name$issue_filter"
  fi

  # First search by class and test name, then by class name only
  issue_id="$(find_issue "in:title $class_name AND in:title $test_name$issue_filter" "$class_name")"
  if [[ -z "$issue_id" ]]; then
    issue_id="$(find_issue "$fallback_search" "$class_name")"
  fi
  if [[ -n "$issue_id" ]]; then
    suffix=" (<https://github.com/camunda/camunda/issues/$issue_id|GH issue>)"
  fi

  printf "• %sx \`%s.%s\`%s\n\n" "$count" "$class_name" "$test_name" "$suffix"

  if [[ "$count" -ge "$threshold" && -z "$issue_id" ]]; then
    medic="$(find_medic "$fqcn")"
    if [[ -n "$medic" ]]; then
      printf ":arrow_right:  %s\n\n" "$medic"
    fi
  fi
}

# Args: JSON array of {test_class_name, test_name, <count_field>}, count field, threshold.
print_tests() {
  local row
  echo "$1" | jq -c '.[]' | while read -r row; do
    print_test \
      "$(echo "$row" | jq -r --arg f "$2" '.[$f]')" \
      "$(echo "$row" | jq -r '.test_class_name')" \
      "$(echo "$row" | jq -r '.test_name')" \
      "$3"
  done
}

summarize_flaky() {
  local all_jobs flaky_jobs top_tests flaky_pct
  # shellcheck disable=SC2016
  all_jobs=$(bq_query 'SELECT COUNT(*) as number_of_all_jobs FROM `ci-30-162810.prod_ci_analytics.build_status_v2` WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 1 DAY)<=report_time AND report_time<=TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY) AND ci_url="https://github.com/camunda/camunda"' | jq -r '.[0].number_of_all_jobs')
  # shellcheck disable=SC2016
  flaky_jobs=$(bq_query 'SELECT COUNT(*) as number_of_all_jobs FROM `ci-30-162810.prod_ci_analytics.build_status_v2` WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 1 DAY)<=report_time AND report_time<=TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY) AND ci_url="https://github.com/camunda/camunda" AND user_reason="flaky-tests"' | jq -r '.[0].number_of_all_jobs')
  # shellcheck disable=SC2016
  top_tests=$(bq_query 'SELECT test_class_name, test_name, COUNT(*) as number_of_flakes FROM `ci-30-162810.prod_ci_analytics.test_status_v1` ts LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs ON ts.ci_url=bs.ci_url AND ts.build_id=bs.build_id AND ts.job_name=bs.job_name WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 1 DAY)<=ts.report_time AND ts.report_time<=TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY) AND ts.ci_url="https://github.com/camunda/camunda" AND test_status = "flaky" GROUP BY test_class_name, test_name ORDER BY number_of_flakes DESC LIMIT 5')

  flaky_pct=$(echo "scale=1; 100 * $flaky_jobs / $all_jobs" | bc)
  [[ "$flaky_pct" == .* ]] && flaky_pct="0${flaky_pct}"

  echo "📈 ~${flaky_pct}% of jobs had flaky tests yesterday ($flaky_jobs from total of $all_jobs jobs)."
  echo ""
  echo ""
  echo "🥇 *Top 5 flakiest tests yesterday:*"
  echo ""
  print_tests "$top_tests" number_of_flakes "$FLAKY_TEST_THRESHOLD"
  echo ""
  echo "📊 Check out <${GRAFANA_URL}?orgId=1&from=now-1d%2Fd&to=now-1d%2Fd&timezone=browser&var-branch=main|Grafana (\`main\`) for links and more details>. Above tests are from all branches, so change the branch in Grafana accordingly."
}

summarize_failure() {
  local top_tests
  # shellcheck disable=SC2016
  top_tests=$(bq_query 'SELECT test_class_name, test_name, COUNT(*) as number_of_fails FROM `ci-30-162810.prod_ci_analytics.test_status_v1` ts LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs ON ts.ci_url=bs.ci_url AND ts.build_id=bs.build_id AND ts.job_name=bs.job_name WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 7 DAY)<=ts.report_time AND ts.report_time<=TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY) AND ts.ci_url="https://github.com/camunda/camunda" AND test_status = "failure" AND ((build_trigger="merge_group" AND build_base_ref="refs/heads/main") OR (build_trigger="push" AND build_ref="refs/heads/main")) GROUP BY test_class_name, test_name ORDER BY number_of_fails DESC LIMIT 5')

  echo ":fire: *Top 5 failing tests on \`main\` last week:*"
  echo ""
  print_tests "$top_tests" number_of_fails "$FAILED_TEST_THRESHOLD"
  echo ""
  echo "📊 Check out <${GRAFANA_URL}?orgId=1&from=now-1w%2Fw&to=now-1w%2Fw&timezone=browser&var-branch=main|Grafana for links and more details>."
}

"summarize_${MODE}"
