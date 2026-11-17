#!/usr/bin/env bash
# Hash the ids in a raw_* CSV without printing them. See ../references/privacy.md.
# Usage: hash_ids.sh <salt file> <csv with one id column>
# Prints a 12-char hash per distinct id, sorted. Stops on any error.
set -euo pipefail

[ $# -eq 2 ] || { echo "usage: hash_ids.sh <salt file> <csv with one id column>" >&2; exit 2; }
salt=$(cat "$1" 2>/dev/null) || salt=""
[ -n "$salt" ] || { echo "No salt in $1, stopping" >&2; exit 1; }
[ -f "$2" ] || { echo "No CSV at $2, stopping" >&2; exit 1; }
if command -v sha256sum >/dev/null; then sha=(sha256sum)
elif command -v shasum >/dev/null; then sha=(shasum -a 256)
else echo "No sha256sum or shasum, stopping" >&2; exit 1; fi
# An odd number of quotes on a line means a quoted id spans lines. Reading line by line
# would split it into several ids.
awk '{ if (gsub(/"/, "&") % 2) { print "Line " NR " holds part of a multiline id, stopping" > "/dev/stderr"; exit 1 } }' "$2"

# Skip the header, MAT's trailing comma, quotes, and ids a truncated dump lost.
awk 'NR>1 { sub(/,$/, ""); gsub(/^"|"$/, ""); if ($0 != "") print }' "$2" |
  sort -u | while IFS= read -r id; do
    h=$(printf '%s%s' "$salt" "$id" | "${sha[@]}") || { echo "Hashing failed, stopping" >&2; exit 1; }
    printf '%s\n' "${h:0:12}"
  done | sort
