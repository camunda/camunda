#!/bin/bash
# Check PR commits behind base branch filtering out commits made by renovate

check_pr_commits() {
  local pr_number="$1"
  local branch_name="$2" 
  local base_branch="$3"
  local repo_owner="$4"
  
  echo "📊 Calculating commits behind for PR #$pr_number (excluding renovate commits)..." >&2
  
  local repo_owner_from_env
  repo_owner_from_env=$(echo "${GITHUB_REPOSITORY}" | cut -d'/' -f1)
  
  # Only process internal branches
  if [[ "$repo_owner" != "$repo_owner_from_env" ]]; then
    echo "0"
    return
  fi
  
  # Get commits and filter renovate
  local commit_list commits_behind
  commit_list=$(git rev-list origin/"$branch_name"..origin/"$base_branch" --pretty=format:"%H|%an|%ae" --no-merges)
  commits_behind=$(echo "$commit_list" | grep "^[a-f0-9]" | grep -c -v "|renovate\[bot\]" | tr -d ' ')
  
  echo "🔧 Found $commits_behind non-renovate commits behind $base_branch" >&2
  echo "$commits_behind"
}
