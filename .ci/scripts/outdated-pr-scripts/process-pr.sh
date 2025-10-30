#!/bin/bash
# Process PRs in batch using our modular functions
# This script reads PR data from stdin (JSON) and processes each PR

set -euo pipefail

# Configuration
COMMIT_THRESHOLD="${COMMIT_THRESHOLD:-100}"
STALE_DAYS_THRESHOLD="${STALE_DAYS_THRESHOLD:-7}"

# Get the script directory and source our library functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/check-pr-commits.sh"
source "$SCRIPT_DIR/lib/update-outdated-pr.sh"

# Check if running in dry-run mode
DRY_RUN="${DRY_RUN:-false}"
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔍 DRY-RUN MODE: Will calculate commits but not add labels/comments"
fi

DATE_PROGRAM=date
if command -v gdate >/dev/null; then
  DATE_PROGRAM=gdate
fi

# Read and process PRs from stdin
while IFS=' ' read -r pr_number branch_name base_branch repo_owner; do
  
  # Skip forked repositories early
  if [[ "$repo_owner" != "${GITHUB_REPOSITORY_OWNER}" ]]; then
    echo "🔀 Skipping forked repository PR #$pr_number from $repo_owner"
    echo "---"
    continue
  fi
  
  echo "🔍 Processing PR #$pr_number ($branch_name from $repo_owner targeting $base_branch)"
  
  # Check commits behind (function will show diagnostic messages)
  commits_behind=$(check_pr_commits "$pr_number" "$branch_name" "$base_branch" "$repo_owner" | tail -1)
  
  # Analysis summary
  echo "📋 Branch analysis: $branch_name is $commits_behind commits behind $base_branch"
  
  # Check branch staleness - only for internal branches that exist
  latest_age_days=0
  if [[ "$repo_owner" == "${GITHUB_REPOSITORY_OWNER}" ]] && git rev-parse --verify "origin/$branch_name" >/dev/null 2>&1; then
    latest_commit_date=$(git log origin/"$base_branch"..origin/"$branch_name" --pretty=format:"%ai" | head -1 2>/dev/null || echo "")
    latest_commit_timestamp=$("$DATE_PROGRAM" -d "$latest_commit_date" +%s 2>/dev/null || echo "0")
    current_timestamp=$("$DATE_PROGRAM" +%s)
    latest_age_days=$(( (current_timestamp - latest_commit_timestamp) / 86400 ))
  fi
  
  # Only call update function if PR is actually outdated
  reasons=()
  [[ "$commits_behind" -gt $COMMIT_THRESHOLD ]] && reasons+=("$commits_behind commits behind")
  [[ $latest_age_days -gt $STALE_DAYS_THRESHOLD ]] && reasons+=("$latest_age_days days stale")

  if [[ ${#reasons[@]} -gt 0 ]]; then
    reason=$(IFS=" and "; echo "${reasons[*]}")
    echo "🚨 PR #$pr_number is outdated: $reason"
    update_outdated_pr "$pr_number" "$commits_behind" "$base_branch" "$COMMIT_THRESHOLD" "$latest_age_days" "$STALE_DAYS_THRESHOLD" "$DRY_RUN"
  else
    echo "✅ PR #$pr_number is up-to-date"
  fi
  
  echo "---"
done < <(jq -r '.[] | "\(.pr_number) \(.branch_name) \(.base_branch) \(.repo_owner)"')
