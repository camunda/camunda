#!/bin/bash
# Check PR commits behind main filtering out commits made by renovate

# Configuration
BASE_BRANCH="${BASE_BRANCH:-main}"

check_pr_commits() {
  local pr_number="$1"
  local branch_name="$2" 
  local repo_owner="$3"
  
  echo "📊 Calculating commits behind for PR #$pr_number (excluding renovate commits)..." >&2
  
  # Fetch latest remote changes to ensure we have up-to-date refs
  echo "🔄 Fetching latest remote changes..." >&2
  if ! git fetch origin; then
    echo "⚠️  Git fetch failed, proceeding with existing refs..." >&2
  fi
  
  local commits_behind
  local repo_owner_from_env
  repo_owner_from_env=$(echo "${GITHUB_REPOSITORY}" | cut -d'/' -f1)
  
  if [[ "$repo_owner" == "$repo_owner_from_env" ]]; then
    # Internal branch - use local git after fetch
    echo "🔍 Using local git to calculate commits (filtering renovate)..." >&2
    if ! git rev-parse --verify "origin/$branch_name" >/dev/null 2>&1; then
      echo "⚠️  Branch origin/$branch_name not found after fetch, defaulting to 0" >&2
      commits_behind="0"
    else
      # Use git to get commits and filter out renovate commits
      if ! commit_list=$(git rev-list origin/"$branch_name"..origin/"$BASE_BRANCH" --pretty=format:"%H|%an|%ae" --no-merges); then
        echo "⚠️  Git rev-list command failed, defaulting to 0" >&2
        commits_behind="0"
      else
        # Filter out renovate commits from git output
        commits_behind=$(echo "$commit_list" | grep "^[a-f0-9]" | grep -c -v "|renovate\[bot\]" | tr -d ' ')
        echo "🔧 Found $commits_behind non-renovate commits behind $BASE_BRANCH" >&2
      fi
    fi
  else
    # External fork - fetch the fork's branch and then use local git
    echo "🌐 Fetching external fork branch..." >&2
    if ! git fetch origin "refs/pull/$pr_number/head:pr-$pr_number" >/dev/null 2>&1; then
      echo "⚠️  Failed to fetch PR branch, defaulting to 0" >&2
      commits_behind="0"
    else
      # Use git to calculate commits for external fork
      if ! commit_list=$(git rev-list pr-"$pr_number"..origin/"$BASE_BRANCH" --pretty=format:"%H|%an|%ae" --no-merges); then
        echo "⚠️  Git rev-list command failed for external fork, defaulting to 0" >&2
        commits_behind="0"
      else
        # Filter out renovate commits from git output
        commits_behind=$(echo "$commit_list" | grep "^[a-f0-9]" | grep -c -v "|renovate\[bot\]" | tr -d ' ')
        echo "🔧 Found $commits_behind non-renovate commits behind $BASE_BRANCH" >&2
      fi
      # Clean up temporary branch
      git branch -D pr-"$pr_number" >/dev/null 2>&1 || true
    fi
  fi
  
  # Validate the result
  if [[ ! "$commits_behind" =~ ^[0-9]+$ ]]; then
    echo "⚠️  Warning: Invalid commits_behind value '$commits_behind', defaulting to 0" >&2
    commits_behind="0"
  fi
  
  echo "📈 PR #$pr_number is $commits_behind commits behind main (excluding renovate)" >&2
  echo "$commits_behind"  # Return value
}
