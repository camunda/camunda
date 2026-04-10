#!/usr/bin/env bash
set -euo pipefail

# Deletes Kubernetes namespaces whose "deadline-date" label is on or before the given date.
# This ensures namespaces are still cleaned up even if the workflow didn't run on their exact deadline day.
#
# Usage: clean-up-load-test-namespaces.sh [--execute] [DATE]
#   --execute: actually delete namespaces (default is dry-run)
#   DATE:      optional date in YYYY-MM-DD format (defaults to today)
#
# Outputs:
#   NAMESPACES: multiline list of deleted namespaces (written to $GITHUB_OUTPUT if set)
#   STATS:      summary counts — total, deleted, retained (written to $GITHUB_OUTPUT if set)

dryRun=true
if [[ "${1:-}" == "--execute" ]]; then
  dryRun=false
  shift
fi

currentDate=$(date -d "${1:-}" +'%Y-%m-%d' 2>/dev/null || date +'%Y-%m-%d')
echo "Date used for deletion: $currentDate"

if [[ "$dryRun" == true ]]; then
  echo "Running in DRY-RUN mode. No namespaces will be deleted. Pass --execute to delete."
fi

namespacesDeleted=""
totalCount=0
deletedCount=0
retainedCount=0

for row in $(kubectl get namespace -l deadline-date -o json | jq -r '.items[] | "\(.metadata.name)=\(.metadata.labels["deadline-date"])"');
do
  ns="${row%%=*}"
  deadlineDate="${row#*=}"
  totalCount=$((totalCount + 1))
  if [[ "${deadlineDate//-/}" -le "${currentDate//-/}" ]]; then
    deletedCount=$((deletedCount + 1))
    if [[ "$dryRun" == true ]]; then
      echo "[DRY-RUN] Would delete namespace: $ns (deadline: $deadlineDate)"
    else
      kubectl delete namespace "$ns" --wait=false
    fi
    namespacesDeleted+=" * $ns (deadline: $deadlineDate)\n"
  else
    retainedCount=$((retainedCount + 1))
  fi
done

echo -e "Namespaces eligible for deletion:\n$namespacesDeleted"
echo "Stats: total=$totalCount deleted=$deletedCount retained=$retainedCount"

stats="*Total namespaces:* $totalCount  |  *Deleted:* $deletedCount  |  *Retained:* $retainedCount"

# Write output for GitHub Actions if running in CI
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo 'NAMESPACES<<EOF'
    echo "$namespacesDeleted"
    echo 'EOF'
  } >> "$GITHUB_OUTPUT"
  {
    echo 'STATS<<EOF'
    echo "$stats"
    echo 'EOF'
  } >> "$GITHUB_OUTPUT"
fi
