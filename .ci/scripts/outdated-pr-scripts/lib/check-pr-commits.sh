#!/bin/bash
# Check PR commits behind base branch filtering out commits made by renovate

check_pr_commits() {
  local pr_number="$1"
  local branch_name="$2" 
  local base_branch="$3"
  local repo_owner="$4"
  
  echo "📊 Calculating commits behind for PR #$pr_number (excluding renovate commits)..." >&2
  
  # Validate branch exists before attempting git log
  if ! git rev-parse --verify "origin/$branch_name" >/dev/null 2>&1; then
    echo "⚠️  Branch origin/$branch_name does not exist (likely from forked repository)"
    echo "0"
    return
  fi
  
  # Get commits and filter renovate
  local commits_behind
  commits_behind=$(git log origin/"$branch_name"..origin/"$base_branch" --pretty=format:"%an|%ae" --no-merges 2>/dev/null | grep -v -E 'renovate\[bot\]|mend\[bot\]' | wc -l)

  echo "$commits_behind"
}
