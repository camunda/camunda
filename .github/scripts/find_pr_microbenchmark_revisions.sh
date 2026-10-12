#!/usr/bin/env bash
set -euo pipefail

base_sha="${1:?Usage: find_pr_microbenchmark_revisions.sh BASE_SHA HEAD_SHA}"
head_sha="${2:?Usage: find_pr_microbenchmark_revisions.sh BASE_SHA HEAD_SHA}"
merge_base="$(git merge-base "$base_sha" "$head_sha")"
first_perf_commit="$(
  git log --first-parent --reverse --format='%H%x09%s' "$merge_base..$head_sha" \
    | awk -F '\t' '$2 ~ /^perf: / && !found { print $1; found = 1 }'
)"

if [[ -n "$first_perf_commit" ]]; then
  baseline_sha="$(git rev-parse "${first_perf_commit}^1")"
  revisions='["baseline","branch tip"]'
  printf 'Using parent of first perf commit (%s) as JMH baseline: %s\n' "$first_perf_commit" "$baseline_sha" >&2
else
  baseline_sha=""
  revisions='["branch tip"]'
  printf 'No first-parent perf: commit found; running JMH at the branch tip only.\n' >&2
fi

printf 'baseline_sha=%s\n' "$baseline_sha"
printf 'revisions=%s\n' "$revisions"
