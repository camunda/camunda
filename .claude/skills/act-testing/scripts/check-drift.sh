#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <production-workflow> <test-workflow>"
  exit 1
}

PROD="${1:-}"
TEST="${2:-}"
[[ -n "$PROD" && -n "$TEST" ]] || usage

[[ -f "$PROD" ]] || { echo "❌ production workflow not found: $PROD"; exit 1; }
[[ -f "$TEST" ]] || { echo "❌ test workflow not found: $TEST"; exit 1; }

tmp_prod="$(mktemp)"
tmp_test="$(mktemp)"
trap 'rm -f "$tmp_prod" "$tmp_test"' EXIT

extract_run_blocks() {
  local src="$1"
  local out="$2"
  awk '
    function leading_spaces(s,  i, c) {
      for (i = 1; i <= length(s); i++) {
        c = substr(s, i, 1)
        if (c != " ") {
          return i - 1
        }
      }
      return length(s)
    }

    {
      line = $0

      if (!in_run) {
        if (line ~ /^[[:space:]]*run:[[:space:]]*\|[[:space:]]*$/) {
          in_run = 1
          run_indent = leading_spaces(line)
        }
        next
      }

      current_indent = leading_spaces(line)

      if (line ~ /^[[:space:]]*$/) {
        print ""
        next
      }

      if (current_indent <= run_indent) {
        in_run = 0
        if (line ~ /^[[:space:]]*run:[[:space:]]*\|[[:space:]]*$/) {
          in_run = 1
          run_indent = leading_spaces(line)
        }
        next
      }

      body_indent = run_indent + 2
      if (current_indent >= body_indent) {
        print substr(line, body_indent + 1)
      } else {
        print line
      }
    }
  ' "$src" > "$out"
}

strip_mock_sections() {
  local src="$1"
  awk '
    /# MOCK-START/ {skip=1; next}
    /# MOCK-END/ {skip=0; next}
    !skip {
      sub(/[[:space:]]+$/, "")
      if ($0 ~ /^[[:space:]]*$/) {
        next
      }
      print
    }
  ' "$src"
}

extract_run_blocks "$PROD" "$tmp_prod"
extract_run_blocks "$TEST" "$tmp_test"

clean_prod="$(mktemp)"
clean_test="$(mktemp)"
trap 'rm -f "$tmp_prod" "$tmp_test" "$clean_prod" "$clean_test"' EXIT

strip_mock_sections "$tmp_prod" > "$clean_prod"
strip_mock_sections "$tmp_test" > "$clean_test"

if diff -u "$clean_prod" "$clean_test" >/tmp/drift.diff 2>&1; then
  echo "✅ No logic drift detected"
  exit 0
fi

echo "❌ DRIFT DETECTED"
cat /tmp/drift.diff
exit 1
