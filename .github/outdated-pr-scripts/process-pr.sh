#!/bin/bash
# Process PRs in batch using our modular functions
# This script reads PR data from stdin (JSON) and processes each PR

set -euo pipefail

# Configuration
BASE_BRANCH="${BASE_BRANCH:-main}"
COMMIT_THRESHOLD="${COMMIT_THRESHOLD:-50}"
STALE_DAYS_THRESHOLD="${STALE_DAYS_THRESHOLD:-7}"

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

# Function to check if a branch is stale based on last commit date
check_branch_staleness() {
  local branch_name="$1"
  
  if git rev-parse --verify "origin/$branch_name" >/dev/null 2>&1; then
    local latest_commit_date
    latest_commit_date=$(git log origin/"$BASE_BRANCH"..origin/"$branch_name" --pretty=format:"%ai" | head -1 2>/dev/null || echo "")
    
    if [[ -n "$latest_commit_date" ]]; then
      local latest_commit_timestamp
      if command -v gdate >/dev/null 2>&1; then
        latest_commit_timestamp=$(gdate -d "$latest_commit_date" +%s 2>/dev/null || echo "0")
      else
        latest_commit_timestamp=$(date -d "$latest_commit_date" +%s 2>/dev/null || echo "0")
      fi
      
      local current_timestamp latest_age_days
      current_timestamp=$(date +%s)
      latest_age_days=$(( (current_timestamp - latest_commit_timestamp) / 86400 ))
      
      if [[ $latest_age_days -gt $STALE_DAYS_THRESHOLD ]]; then
        echo "true"
      else
        echo "false"
      fi
    else
      echo "false"
    fi
  else
    echo "false"
  fi
}

# Function to check if a PR is outdated (by commits or staleness)
check_pr_outdated() {
  local commits_behind="$1"
  local is_stale="$2"
  
  if [[ "$commits_behind" -gt $COMMIT_THRESHOLD ]] || [[ "$is_stale" == "true" ]]; then
    echo "true"
  else
    echo "false"
  fi
}

# Function to calculate branch age for display
get_branch_age_display() {
  local branch_name="$1"
  
  if ! git rev-parse --verify "origin/$branch_name" >/dev/null 2>&1; then
    echo "Unknown"
    return
  fi
  
  local oldest_commit_date
  oldest_commit_date=$(git log origin/"$BASE_BRANCH"..origin/"$branch_name" --pretty=format:"%ai" --reverse | head -1 2>/dev/null || echo "")
  
  if [[ -z "$oldest_commit_date" ]]; then
    echo "Unknown"
    return
  fi
  
  local commit_timestamp
  if command -v gdate >/dev/null 2>&1; then
    commit_timestamp=$(gdate -d "$oldest_commit_date" +%s 2>/dev/null || echo "0")
  else
    commit_timestamp=$(date -d "$oldest_commit_date" +%s 2>/dev/null || echo "0")
  fi
  
  local current_timestamp age_hours age_days
  current_timestamp=$(date +%s)
  age_hours=$(( (current_timestamp - commit_timestamp) / 3600 ))
  age_days=$(( age_hours / 24 ))
  
  if [[ $age_days -gt 0 ]]; then
    echo "${age_days}d"
  else
    echo "${age_hours}h"
  fi
}

# Function to get PR details for summary
get_pr_summary_details() {
  local pr_number="$1"
  local branch_name="$2"
  local commits_behind="$3"
  
  local pr_details pr_title pr_author age_display pr_url
  pr_details=$(gh pr view "$pr_number" --json title,createdAt,author,url --jq '{title: .title, created: .createdAt, author: .author.login, url: .url}' 2>/dev/null || echo '{"title":"Unknown","created":"Unknown","author":"Unknown","url":""}')
  pr_title=$(echo "$pr_details" | jq -r '.title' | cut -c1-80)
  pr_author=$(echo "$pr_details" | jq -r '.author')
  pr_url=$(echo "$pr_details" | jq -r '.url')
  age_display=$(get_branch_age_display "$branch_name")
  
  # Make PR number clickable if URL is available
  if [[ -n "$pr_url" && "$pr_url" != "null" ]]; then
    echo "| [#$pr_number]($pr_url) | $pr_author | $commits_behind | $age_display | $pr_title |"
  else
    echo "| #$pr_number | $pr_author | $commits_behind | $age_display | $pr_title |"
  fi
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

# Get the script directory and source our library functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/check-pr-commits.sh"
source "$SCRIPT_DIR/lib/update-outdated-pr.sh"

# Check if running in dry-run mode
DRY_RUN="${DRY_RUN:-false}"
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔍 DRY-RUN MODE: Will calculate commits but not add labels/comments"
fi

echo "Processing all PRs in single runner..."
git fetch origin --quiet

# Read PR list from stdin and process each PR
outdated_prs=0
up_to_date_prs=0
total_outdated_commits=0
outdated_pr_details=""
prs_above_commit_threshold=0

# Read the entire stdin into a variable to check if single PR or batch
pr_input=$(cat)
total_pr_count=$(echo "$pr_input" | jq '. | length')

echo "📊 Processing $total_pr_count PR(s)..."
if [[ $total_pr_count -eq 1 ]]; then
  echo "🔍 Single PR mode detected"
else
  echo "📦 Batch mode detected"
fi

# Process using a here-string to avoid subshell variable scope issues
while IFS=' ' read -r pr_number branch_name repo_owner; do
  echo "🔍 Processing PR #$pr_number ($branch_name from $repo_owner)"
  
  # Use our modular functions
  commits_behind=$(check_pr_commits "$pr_number" "$branch_name" "$repo_owner")
  
  # Check staleness for all PRs (used in both summary and counting logic)
  is_stale=$(check_branch_staleness "$branch_name")
  is_outdated=$(check_pr_outdated "$commits_behind" "$is_stale")
  
  # Get PR details for summary (if we're collecting summary data)
  if [[ -n "${GITHUB_OUTPUT:-}" ]] && [[ "$is_outdated" == "true" ]]; then
    pr_details=$(get_pr_summary_details "$pr_number" "$branch_name" "$commits_behind")
    if [[ -n "$outdated_pr_details" ]]; then
      outdated_pr_details="$outdated_pr_details"$'\n'"$pr_details"
    else
      outdated_pr_details="$pr_details"
    fi
  fi
  
  # Count PRs for statistics (applies to both summary and non-summary runs)
  if [[ "$is_outdated" == "true" ]]; then
    outdated_prs=$((outdated_prs + 1))
    # Add commits to total for average calculation (only for PRs that exceed commit threshold)
    if [[ "$commits_behind" -gt $COMMIT_THRESHOLD ]]; then
      total_outdated_commits=$((total_outdated_commits + commits_behind))
      prs_above_commit_threshold=$((prs_above_commit_threshold + 1))
    fi
  else
    up_to_date_prs=$((up_to_date_prs + 1))
  fi
  
  # Only call update function if PR is actually outdated
  if [[ "$is_outdated" == "true" ]]; then
    update_outdated_pr "$pr_number" "$commits_behind" "$COMMIT_THRESHOLD" "$DRY_RUN"
  fi
  
  echo "---"
done < <(echo "$pr_input" | jq -r '.[] | "\(.pr_number) \(.branch_name) \(.repo_owner)"')

# Add summary statistics if this is a daily run with multiple PRs
if [[ $total_pr_count -gt 1 ]]; then
  # Calculate total from the sum of categorized PRs
  total_prs=$((outdated_prs + up_to_date_prs))
  
  # Calculate average commit difference for outdated PRs (those above threshold)
  outdated_prs_above_threshold=0
  if [[ -n "$outdated_pr_details" ]]; then
    outdated_prs_above_threshold=$(echo "$outdated_pr_details" | wc -l | tr -d ' ')
  fi
  
  average_commit_diff="N/A"
  if [[ $prs_above_commit_threshold -gt 0 && $total_outdated_commits -gt 0 ]]; then
    average_commit_diff=$((total_outdated_commits / prs_above_commit_threshold))
  fi
  
  # Create summary in memory and output to GitHub Actions
  summary="## Summary Statistics

| Metric | Count |
|--------|-------|
| Total PRs Processed | $total_prs |
| Outdated PRs (more than $COMMIT_THRESHOLD commits diff) | $outdated_prs |
| Up-to-date PRs | $up_to_date_prs |
| Average commit diff of outdated PRs (exceeding $COMMIT_THRESHOLD commit treshold) | $average_commit_diff |
"
  
  if [[ "$outdated_prs" -gt 0 ]]; then
    summary="$summary
## Outdated PRs Details

| PR# | Author | Commits Behind | Age | Title |
|-----|--------|----------------|-----|-------|
$outdated_pr_details"
  else
    summary="$summary
🎉 All PRs are up-to-date!"
  fi
  
  # Output summary to GitHub Actions step summary
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    echo "$summary" >> "$GITHUB_STEP_SUMMARY"
  fi
fi
