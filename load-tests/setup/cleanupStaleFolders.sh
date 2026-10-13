#!/bin/bash

# Removes local load test folders (c8-*) whose Kubernetes namespace no longer exists.
# Dry run by default; pass --delete to remove the folders.
# A folder is named after its namespace (see newLoadTest.sh). Renaming either one by hand
# makes the folder look stale, which is the responsibility of whoever renamed it.

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<USAGE
Usage: cleanupStaleFolders.sh [--delete]

Lists load test folders in ${SCRIPT_DIR} whose namespace does not exist in the
current kubectl context. Nothing is removed unless --delete is given.
The folders are git-ignored, so deletion cannot be undone.
USAGE
}

delete=false
if [[ $# -gt 1 ]]; then
  usage >&2
  exit 1
fi
case "${1:-}" in
  "") ;;
  --delete) delete=true ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 1 ;;
esac

context=$(kubectl config current-context)
# Assigned on its own line so a failed listing aborts instead of flagging every folder as stale.
namespaces=$(kubectl get namespaces -o name)
namespaces=${namespaces//namespace\//}
if [[ -z "$namespaces" ]]; then
  echo "kubectl returned no namespaces, refusing to continue." >&2
  exit 1
fi

echo "kubectl context: ${context}"

stale=()
for dir in "${SCRIPT_DIR}"/c8-*/; do
  [[ -d "$dir" ]] || continue
  folder=$(basename "$dir")
  if ! grep -qxF "$folder" <<<"$namespaces"; then
    stale+=("$folder")
  fi
done

if [[ ${#stale[@]} -eq 0 ]]; then
  echo "No stale folders."
  exit 0
fi

echo "Stale folders (no namespace):"
printf '  %s\n' "${stale[@]}"

if [[ "$delete" != true ]]; then
  echo "Dry run. Re-run with --delete to remove them."
  exit 0
fi

for folder in "${stale[@]}"; do
  rm -rf -- "${SCRIPT_DIR:?}/${folder:?}"
  echo "Removed ${folder}"
done
