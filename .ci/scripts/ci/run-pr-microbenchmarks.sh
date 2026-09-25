#!/usr/bin/env bash
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.

set -euo pipefail

: "${BASE_SHA:?BASE_SHA must be set}"
: "${HEAD_SHA:?HEAD_SHA must be set}"
: "${JMH_RESULTS_DIR:?JMH_RESULTS_DIR must be set}"
: "${JMH_REPORTED_MARKERS:?JMH_REPORTED_MARKERS must be set}"
: "${JMH_FAILURE_FILE:?JMH_FAILURE_FILE must be set}"
CHANGED_JAVA_FILES_JSON=${CHANGED_JAVA_FILES_JSON:-[]}
PR_BODY=${PR_BODY:-}

mkdir -p "$JMH_RESULTS_DIR"
rm -f "$JMH_FAILURE_FILE"
trap 'git checkout --detach "$HEAD_SHA" >/dev/null 2>&1 || true' EXIT

body_without_comments=$(PR_BODY="$PR_BODY" python3 - <<'PY'
import os
import re

print(re.sub(r"<!--.*?-->", "", os.environ.get("PR_BODY", ""), flags=re.DOTALL))
PY
)

explicit_benchmarks=()
while IFS= read -r body_line; do
  if [[ "$body_line" =~ ^[[:space:]]*[Jj][Mm][Hh]:[[:space:]]*(.*)$ ]]; then
    directive=${BASH_REMATCH[1]//,/ }
    directive=${directive//\`/}
    read -r -a line_benchmarks <<< "$directive"
    for selector in "${line_benchmarks[@]}"; do
      if [[ ! "$selector" =~ ^[A-Za-z0-9_$]+(\.[A-Za-z0-9_$]+)*$ ]]; then
        echo "::error::Invalid JMH selector '$selector'. Use class or class.method identifiers on a JMH: line."
        exit 1
      fi
      explicit_benchmarks+=("$selector")
    done
  fi
done <<< "$body_without_comments"

selection_marker_suffix=""
if ((${#explicit_benchmarks[@]} > 0)); then
  mapfile -t explicit_benchmarks < <(printf '%s\n' "${explicit_benchmarks[@]}" | LC_ALL=C sort -u)
  selection_hash=$(printf '%s\n' "${explicit_benchmarks[@]}" | sha256sum | cut -d ' ' -f 1)
  selection_marker_suffix=":$selection_hash"
fi

if ! jq -e 'type == "array" and all(.[]; type == "string")' <<< "$CHANGED_JAVA_FILES_JSON" >/dev/null; then
  echo '::error::Changed Java files must be supplied as a JSON string array.'
  exit 1
fi
mapfile -t changed_java_files < <(jq -r '.[]' <<< "$CHANGED_JAVA_FILES_JSON")

baseline_sha=$(git merge-base "$BASE_SHA" "$HEAD_SHA")
benchmark_files=()
for file in "${changed_java_files[@]}"; do
  if [[ "$file" != microbenchmarks/* || "$file" != *.java ]]; then
    echo "::error::Unexpected path in changed benchmark files: '$file'"
    exit 1
  fi
  if git show "$HEAD_SHA:$file" 2>/dev/null | grep -Eq '^[[:space:]]*(@[[:alnum:]_.]+[[:space:]]*)*@([[:alnum:]_]+\.)*Benchmark([^[:alnum:]_]|$)'; then
    benchmark_files+=("$file")
  fi
done

if ((${#benchmark_files[@]} == 0 && ${#explicit_benchmarks[@]} == 0)); then
  echo "No changed Java benchmark files or explicit JMH selectors were found."
  {
    echo '## JMH PR benchmarks'
    echo "No changed Java files under \`microbenchmarks/\` contain a JMH \`@Benchmark\` annotation, and the PR description has no \`JMH:\` selector line."
  } >> "$GITHUB_STEP_SUMMARY"
  exit 0
fi

mapfile -t perf_commits < <(
  git log --no-merges --reverse --format='%H%x09%s' "$baseline_sha..$HEAD_SHA" \
    | awk -F '\t' '$2 ~ /^perf(\([^)]*\))?!?:/ { print $1 }'
)

append_class_selector() {
  local candidate=$1
  local existing
  local reduced_selectors=()

  for existing in "${selectors[@]}"; do
    if [[ "$existing" != "$candidate".* ]]; then
      reduced_selectors+=("$existing")
    fi
  done
  selectors=("${reduced_selectors[@]}")

  for existing in "${selectors[@]}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return
    fi
  done
  selectors+=("$candidate")
}

append_method_selector() {
  local candidate=$1
  local candidate_class=${candidate%.*}
  local existing

  for existing in "${selectors[@]}"; do
    if [[ "$existing" == "$candidate_class" || "$existing" == "$candidate" ]]; then
      return
    fi
  done
  selectors+=("$candidate")
}

run_revision() {
  local revision_type=$1
  local revision_sha=$2
  local marker="<!-- jmh-run:${revision_type}:${revision_sha}${selection_marker_suffix} -->"
  local result_file
  result_file="$JMH_RESULTS_DIR/$(printf '%04d-%s-%s.md' "$result_index" "$revision_type" "$revision_sha")"
  local build_log
  local listing_log
  local jmh_log
  local auto_selectors=()
  local selectors=()
  local available_benchmarks=()
  local unavailable_selectors=()
  local file
  local source
  local package_name
  local class_name
  local selector
  local requested
  local benchmark
  local benchmark_class
  local matches_request
  local build_status
  local run_status
  local had_failures=false

  if grep -Fqx "$marker" "$JMH_REPORTED_MARKERS"; then
    echo "Already benchmarked: $marker"
    return
  fi

  git checkout --detach "$revision_sha" >/dev/null

  for file in "${benchmark_files[@]}"; do
    if ! source=$(git show "$revision_sha:$file" 2>/dev/null); then
      continue
    fi
    if ! grep -Eq '^[[:space:]]*(@[[:alnum:]_.]+[[:space:]]*)*@([[:alnum:]_]+\.)*Benchmark([^[:alnum:]_]|$)' <<< "$source"; then
      continue
    fi

    package_name=$(awk '/^[[:space:]]*package[[:space:]]/ { gsub(/[;[:space:]]/, ""); sub(/^package/, ""); print; exit }' <<< "$source")
    class_name=${file##*/}
    class_name=${class_name%.java}
    selector=${class_name}
    if [[ -n "$package_name" ]]; then
      selector="$package_name.$class_name"
    fi
    auto_selectors+=("$selector")
  done

  {
    printf '%s\n\n' "$marker"
    printf "## JMH %s results for \`%s\`\n\n" "$revision_type" "$revision_sha"
  } > "$result_file"

  if ((${#auto_selectors[@]} == 0 && ${#explicit_benchmarks[@]} == 0)); then
    echo 'No selected changed benchmark source exists at this revision; benchmark execution was skipped.' >> "$result_file"
    return
  fi

  build_log=$(mktemp)
  if ./mvnw -pl microbenchmarks -am -DskipTests clean package > "$build_log" 2>&1; then
    listing_log=$(mktemp)
    if java -jar microbenchmarks/target/benchmarks.jar -l > "$listing_log" 2>&1; then
      mapfile -t available_benchmarks < <(awk '/^[[:alnum:]_$][[:alnum:]_.$]*$/ { print }' "$listing_log")
    else
      run_status=$?
      had_failures=true
      {
        printf 'Could not list available JMH benchmarks (exit %s).\n\n' "$run_status"
        tail -n 80 "$listing_log"
      } >> "$result_file"
    fi
    rm -f "$listing_log"

    if [[ "$had_failures" == false ]]; then
      selectors=("${auto_selectors[@]}")
      for requested in "${explicit_benchmarks[@]}"; do
        matches_request=false
        for benchmark in "${available_benchmarks[@]}"; do
          benchmark_class=${benchmark%.*}
          if [[ "$benchmark_class" == "$requested" || "$benchmark_class" == *".$requested" ]]; then
            append_class_selector "$benchmark_class"
            matches_request=true
          elif [[ "$benchmark" == "$requested" || "$benchmark" == *".$requested" ]]; then
            append_method_selector "$benchmark"
            matches_request=true
          fi
        done
        if [[ "$matches_request" == false ]]; then
          unavailable_selectors+=("$requested")
        fi
      done
    fi
  else
    build_status=$?
    had_failures=true
    {
      printf 'Maven benchmark build failed with status %s.\n\n' "$build_status"
      echo 'Last 120 lines of build output:'
      tail -n 120 "$build_log"
    } >> "$result_file"
  fi
  rm -f "$build_log"

  if ((${#unavailable_selectors[@]} > 0)); then
    {
      echo 'Requested selectors unavailable at this revision:'
      for selector in "${unavailable_selectors[@]}"; do
        printf -- "- \`%s\`\n" "$selector"
      done
      echo
    } >> "$result_file"
  fi

  if ((${#selectors[@]} == 0)); then
    if [[ "$had_failures" == false ]]; then
      echo 'No selected benchmark is available at this revision; benchmark execution was skipped.' >> "$result_file"
    fi
    if [[ "$had_failures" == true ]]; then
      touch "$JMH_FAILURE_FILE"
    fi
    return
  fi

  {
    printf "Benchmarks: \`%s\`\n\n" "${selectors[*]}"
    echo '```text'
  } >> "$result_file"

  for selector in "${selectors[@]}"; do
    jmh_log=$(mktemp)
    echo "Running JMH selector: $selector"
    if java -jar microbenchmarks/target/benchmarks.jar "$selector" > "$jmh_log" 2>&1; then
      cat "$jmh_log" >> "$result_file"
    else
      run_status=$?
      had_failures=true
      cat "$jmh_log" >> "$result_file"
      printf "\nJMH exited with status %s for \`%s\`.\n" "$run_status" "$selector" >> "$result_file"
    fi
    rm -f "$jmh_log"
  done

  echo '```' >> "$result_file"
  if [[ "$had_failures" == true ]]; then
    touch "$JMH_FAILURE_FILE"
  fi

  # The workflow uses local actions from the pull request head after this script exits.
  git checkout --detach "$HEAD_SHA" >/dev/null
}

result_index=0
run_revision baseline "$baseline_sha"
result_index=$((result_index + 1))
for commit_sha in "${perf_commits[@]}"; do
  run_revision commit "$commit_sha"
  result_index=$((result_index + 1))
done

git checkout --detach "$HEAD_SHA" >/dev/null

echo "Benchmark result files: $(find "$JMH_RESULTS_DIR" -maxdepth 1 -type f -name '*.md' | wc -l)"
