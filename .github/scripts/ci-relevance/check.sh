#!/usr/bin/env bash
# Reads filenames from stdin (one per line).
# Exits 0 if any CI-relevant .github/ change is found, exits 1 otherwise.
#
# A .github/ change is CI-relevant if it does NOT match any pattern in the
# excluded-paths.txt file. Non-.github/ files (Java, docs, etc.) are ignored
# here — they are handled by other path filters in the CI pipeline.
#
# Usage:
#   jq -r '.[]' <<< "$CHANGED_FILES_JSON" | ./check.sh [patterns-file]
#
# Arguments:
#   patterns-file  Path to the exclusion patterns file (default: excluded-paths.txt
#                  alongside this script)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_FILE="${1:-$SCRIPT_DIR/excluded-paths.txt}"

while IFS= read -r file; do
  # Only consider .github/ files — everything else is handled by other filters
  [[ "$file" == .github/* ]] || continue

  excluded=false
  while IFS= read -r pattern || [[ -n "$pattern" ]]; do
    # Skip empty lines and comments
    [[ -z "$pattern" || "$pattern" == \#* ]] && continue
    # Bash glob match — $pattern must be unquoted for glob expansion
    # shellcheck disable=SC2254
    if [[ "$file" == $pattern ]]; then
      excluded=true
      break
    fi
  done < "$PATTERNS_FILE"

  if [[ "$excluded" == false ]]; then
    exit 0  # Found a CI-relevant .github/ file
  fi
done

exit 1  # No CI-relevant .github/ files found
