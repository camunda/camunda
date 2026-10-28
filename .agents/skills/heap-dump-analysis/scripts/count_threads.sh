#!/usr/bin/env bash
# Count threads per stack pattern without printing stack lines. See ../references/queries.md.
# Usage: count_threads.sh <stem>.threads <pattern> [pattern ...]
# Prints "<count> <pattern>" per pattern. A pattern is an awk regex.
set -euo pipefail

[ $# -ge 2 ] || { echo "usage: count_threads.sh <stem>.threads <pattern> [pattern ...]" >&2; exit 2; }
threads=$1
shift
[ -f "$threads" ] || { echo "No threads file at $threads, stopping" >&2; exit 1; }
grep -q '^Thread 0x' "$threads" || { echo "No threads in $threads, stopping" >&2; exit 1; }

for p in "$@"; do
  n=$(awk -v p="$p" '/^Thread 0x/ { t++ } $0 ~ p && !seen[t]++ { n++ } END { print n+0 }' "$threads")
  printf '%s %s\n' "$n" "$p"
done
