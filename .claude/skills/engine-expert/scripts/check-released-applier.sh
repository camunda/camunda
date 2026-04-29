#!/usr/bin/env bash
# Usage: check-released-applier.sh <path-to-applier-file>
# Prints 'RELEASED (first seen in <tag>)' if a release contains this file, or "UNRELEASED" if none.
# Exit code 0 = released, 1 = unreleased.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <path-to-applier-file>" >&2
  exit 2
fi

file="$1"

# Filter to release tags only (X.Y.Z, no -rc/-alpha/etc.). The `|| true`
# tolerates `grep` exiting 1 when no tags match, which would otherwise
# abort the script under `set -o pipefail` and skip the UNRELEASED path.
tags=$(git tag -l | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V || true)

for tag in $tags; do
  if git cat-file -e "$tag:$file" 2>/dev/null; then
    echo "RELEASED (first seen in $tag)"
    exit 0
  fi
done
echo "UNRELEASED"
exit 1
