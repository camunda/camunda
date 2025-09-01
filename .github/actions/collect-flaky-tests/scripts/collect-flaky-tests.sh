#!/bin/bash
# Helper functions for collecting flaky test data
# owner: @camunda/monorepo-devops-team

set -euo pipefail
flaky_data='[]'

# Helper function to add job data to JSON array
add_job_data() {
  local job_name="$1"
  local flaky_tests="$2"
  flaky_tests=$(echo "$flaky_tests" | xargs)
  if [[ -n "$flaky_tests" ]]; then
    echo "Adding job: $job_name with flaky test: $flaky_tests"

    # Append to flaky_data JSON array safely using jq
    flaky_data=$(echo "${flaky_data:-[]}" | jq --arg job "$job_name" --arg tests "$flaky_tests" \
      '. += [{"job": $job, "flaky_tests": $tests}]')
  fi
}

# Helper function to add job data from single jobs
collect_from_single_job() {
  local job_id="$1"
  local result="$2"
  local flaky_tests="$3"

  if [[ "$result" != "skipped" ]]; then
  add_job_data "$job_id" "$flaky_tests"
  fi
}

# Helper function to add job data from matrix jobs
collect_from_matrix_job() {
  local job_id="$1"
  local result="$2"
  local output_json="$3"

  if [[ "$result" != "skipped" ]]; then
    echo "Raw ${job_id} matrix JSON:" && echo "$output_json" | jq '.'

    if echo "$output_json" | jq -e '.flakyTests and (.flakyTests | type == "object")' > /dev/null 2>&1; then
      while IFS= read -r entry; do
        local job_name
        local flaky_tests
        job_name=$(echo "$entry" | jq -r '.key')
        flaky_tests=$(echo "$entry" | jq -r '.value // ""')
        add_job_data "${job_id}/${job_name}" "$flaky_tests"
      done < <(echo "$output_json" | jq -c '.flakyTests | to_entries[]')
    else
      echo "flakyTests is missing or not an object for $job_id"
    fi
  fi
}
