#!/usr/bin/env bash
# Deletes the generated, git-ignored per-load-test directories (load-tests/setup/c8-*) whose
# Kubernetes namespace no longer exists. Dry run by default.
#
# Usage: load-tests/setup/clean-stale-setup-dirs.sh [--apply]
#
# The namespace is the `name` value in <dir>/load-test-setup-values.yaml (falling back to the
# directory name). Uses the current kubectl context; aborts if the cluster is unreachable so a
# connectivity problem never looks like "no namespaces".
set -euo pipefail

apply=false
[[ "${1:-}" == "--apply" ]] && apply=true

setup_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! namespaces=$(kubectl get namespaces -o name 2>/dev/null); then
  echo "kubectl cannot list namespaces in the current context; aborting" >&2
  exit 1
fi

removed=0
kept=0
for dir in "$setup_dir"/c8-*/; do
  [[ -d "$dir" ]] || continue
  dir="${dir%/}"
  base="$(basename "$dir")"
  values="$dir/load-test-setup-values.yaml"
  ns=""
  [[ -f "$values" ]] && ns=$(sed -nE 's/^name:[[:space:]]*"?([^"[:space:]]+)"?.*/\1/p' "$values" | head -n1)
  ns="${ns:-$base}"

  if grep -qx "namespace/$ns" <<<"$namespaces"; then
    echo "keep    $base (namespace $ns exists)"
    kept=$((kept + 1))
  else
    echo "remove  $base (namespace $ns not found)"
    removed=$((removed + 1))
    $apply && rm -rf "$dir"
  fi
done

$apply || echo "Dry run: re-run with --apply to delete."
echo "removed=$removed kept=$kept"
