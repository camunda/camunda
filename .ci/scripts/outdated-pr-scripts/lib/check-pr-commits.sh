#!/bin/bash
# Check PR commits behind base branch filtering out commits made by renovate

check_pr_commits() {
  local pr_number="$1"
  local branch_name="$2" 
  local base_branch="$3"
  local repo_owner="$4"
  
  echo "📊 Calculating commits behind for PR #$pr_number (excluding renovate commits)..." >&2
  
  # Only process internal branches
  if [[ "$repo_owner" != "${GITHUB_REPOSITORY_OWNER}" ]]; then
    echo "0"
    return
  fi
  
  # Get commits and filter renovate
  local commits_behind
  commits_behind=$(git log origin/"$branch_name"..origin/"$base_branch" --pretty=format:"%an|%ae" --no-merges | grep -v -E 'renovate\[bot\]|mend\[bot\]' | wc -l)
  
  echo "🔧 Found $commits_behind non-renovate and non-mend commits behind $base_branch" >&2
  echo "$commits_behind"
}
