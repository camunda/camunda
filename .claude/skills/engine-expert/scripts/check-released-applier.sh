#!/usr/bin/env bash
# Usage: check-released-applier.sh <path-to-applier-file>
# Prints the first release tag containing this file, or "UNRELEASED" if none.
# Exit code 0 = released, 1 = unreleased.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <path-to-applier-file>" >&2
  exit 2
fi

file="$1"
for tag in $(git tag -l | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V); do
  if git cat-file -e "$tag:$file" 2>/dev/null; then
    echo "RELEASED (first seen in $tag)"
    exit 0
  fi
done
echo "UNRELEASED"
exit 1
