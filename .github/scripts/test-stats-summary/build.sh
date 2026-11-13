#!/usr/bin/env bash
# Builds the Slack summary of the tests that flaked (daily) or failed (weekly) most often in
# camunda/camunda CI, from CI Analytics in BigQuery, with the top tests of each supported branch.
#
# Usage: build.sh flaky|failure <messages.json>
#
#   flaky    previous day, push, merge queue and PR runs, medic ping at FLAKY_TEST_THRESHOLD
#   failure  previous 7 days, push and merge queue runs, medic ping at FAILED_TEST_THRESHOLD
#
# Runs count under their base branch. A medic is pinged only when no GH issue matches the test.
#
# Writes a JSON array of Slack messages ({text, blocks}): the top-level message with the first
# branch in SUPPORTED_BRANCHES, then one thread reply per other branch with tests. post.sh posts
# them.
#
# Run from the repo root: the medic lookup maps a test class to its source file with git.
# Needs bq (authenticated), gh (GH_TOKEN), codeowners-cli and jq on PATH.

set -euo pipefail

MODE="${1:-}"
OUT_FILE="${2:-}"
if [[ ! "$MODE" =~ ^(flaky|failure)$ || -z "$OUT_FILE" ]]; then
  echo "usage: $0 flaky|failure <messages.json>" >&2
  exit 2
fi

# main goes in the top-level message, the stable branches in its thread. Update this list at each
# minor release: add the new stable/8.x branch and remove branches that move to ESUP.
SUPPORTED_BRANCHES=(main stable/8.10 stable/8.9 stable/8.8)
TOP_TESTS_PER_BRANCH=3
FLAKY_TEST_THRESHOLD=10
FAILED_TEST_THRESHOLD=3

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=.ci/scripts/ci/setup-medic-lookup.sh
source "${SCRIPT_DIR}/../../../.ci/scripts/ci/setup-medic-lookup.sh"

if [[ "$MODE" == "flaky" ]]; then
  DAYS=1
  TRIGGERS=(push merge_group pull_request)
  THRESHOLD="$FLAKY_TEST_THRESHOLD"
  ISSUE_FILTER=" AND (label:kind/flake OR in:title flaky)"
else
  DAYS=7
  TRIGGERS=(push merge_group)
  THRESHOLD="$FAILED_TEST_THRESHOLD"
  ISSUE_FILTER=""
fi

json_array() {
  jq -cn '$ARGS.positional' --args "$@"
}

bq_query() {
  bq query --format=json --use_legacy_sql=false --quiet=true "$@"
}

top_tests() {
  # bq would read a leading "--" comment line as a flag, so drop the comment lines
  bq_query \
    --parameter="days:INT64:${DAYS}" \
    --parameter="test_status:STRING:${MODE}" \
    --parameter="triggers:ARRAY<STRING>:$(json_array "${TRIGGERS[@]}")" \
    --parameter="branches:ARRAY<STRING>:$(json_array "${SUPPORTED_BRANCHES[@]}")" \
    --parameter="top:INT64:${TOP_TESTS_PER_BRANCH}" \
    "$(sed '/^[[:space:]]*--/d' "${SCRIPT_DIR}/top-tests.sql")"
}

# The share of jobs with flaky tests over all runs, as in the summary before the branch breakdown
flaky_header() {
  local all_jobs flaky_jobs flaky_pct
  # shellcheck disable=SC2016
  all_jobs=$(bq_query 'SELECT COUNT(*) as number_of_all_jobs FROM `ci-30-162810.prod_ci_analytics.build_status_v2` WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 1 DAY)<=report_time AND report_time<=TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY) AND ci_url="https://github.com/camunda/camunda"' | jq -r '.[0].number_of_all_jobs')
  # shellcheck disable=SC2016
  flaky_jobs=$(bq_query 'SELECT COUNT(*) as number_of_all_jobs FROM `ci-30-162810.prod_ci_analytics.build_status_v2` WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL 1 DAY)<=report_time AND report_time<=TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY) AND ci_url="https://github.com/camunda/camunda" AND user_reason="flaky-tests"' | jq -r '.[0].number_of_all_jobs')

  flaky_pct=$(echo "scale=1; 100 * $flaky_jobs / $all_jobs" | bc)
  [[ "$flaky_pct" == .* ]] && flaky_pct="0${flaky_pct}"
  echo "📈 ~${flaky_pct}% of jobs had flaky tests yesterday ($flaky_jobs from total of $all_jobs jobs)."
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

# Reads the rows of top-tests.sql and prints each with its display name, GH issue and due medic.
# A test on several branches is looked up once.
enrich() {
  local row fqcn test_name count class_name name fallback_search key issue medic
  declare -A issues medics

  while read -r row; do
    fqcn="$(jq -r '.test_class_name' <<< "$row")"
    test_name="$(jq -r '.test_name' <<< "$row")"
    count="$(jq -r '.occurrences' <<< "$row")"

    if [[ "$fqcn" == *.ts || "$fqcn" == *.tsx ]]; then
      # Spec path (e.g. src/foo/Bar.test.tsx) -> Bar, as flaky-test issue titles name it
      class_name="${fqcn##*/}"
      class_name="${class_name%%.*}"
      # Spec names like Processes or login are too generic to match on their own
      if [[ "$MODE" == "flaky" ]]; then
        fallback_search="in:title \"$class_name\"$ISSUE_FILTER"
      else
        fallback_search="in:title flaky \"$class_name\""
      fi
    else
      class_name="${fqcn##*.}"
      fallback_search="in:title $class_name$ISSUE_FILTER"
    fi

    # A test without a name failed in the class setup, e.g. in @BeforeAll
    if [[ -n "$test_name" ]]; then
      name="$class_name.$test_name"
    else
      name="$class_name (class setup)"
    fi

    key="$fqcn#$test_name"
    if [[ -z "${issues["$key"]+x}" ]]; then
      issue=""
      if [[ -n "$test_name" ]]; then
        issue="$(find_issue "in:title $class_name AND in:title $test_name$ISSUE_FILTER" "$class_name")"
      fi
      if [[ -z "$issue" ]]; then
        issue="$(find_issue "$fallback_search" "$class_name")"
      fi
      issues["$key"]="$issue"
    fi
    issue="${issues["$key"]}"

    medic=""
    if [[ "$count" -ge "$THRESHOLD" && -z "$issue" ]]; then
      if [[ -z "${medics["$fqcn"]+x}" ]]; then
        medics["$fqcn"]="$(find_medic "$fqcn")"
      fi
      medic="${medics["$fqcn"]}"
    fi

    jq -c --arg name "$name" --arg issue "$issue" --arg medic "$medic" '{
      branch,
      count: (.occurrences | tonumber),
      tests_on_branch: (.tests_on_branch | tonumber),
      name: $name,
      issue: (if $issue == "" then null else $issue end),
      medic: (if $medic == "" then null else $medic end),
      links
    }' <<< "$row"
  done < <(jq -c '.[]')
}

header=""
if [[ "$MODE" == "flaky" ]]; then
  header="$(flaky_header)"
fi

tests="$(top_tests | enrich | jq -s -c '.')"

messages="$(jq -n \
  --arg mode "$MODE" \
  --arg header "$header" \
  --argjson top "$TOP_TESTS_PER_BRANCH" \
  --argjson branches "$(json_array "${SUPPORTED_BRANCHES[@]}")" \
  --argjson tests "$tests" \
  -f "${SCRIPT_DIR}/render.jq")"

jq -r '.[] | select(.shortened) | "::warning::Shortened the Slack message \"\(.text)\" to fit the block size limit"' <<< "$messages" >&2
jq '[.[] | del(.shortened)]' <<< "$messages" > "$OUT_FILE"
