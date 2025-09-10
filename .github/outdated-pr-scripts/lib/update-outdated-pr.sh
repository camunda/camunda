#!/bin/bash
# Update outdated PR with label and comment

# Use configurable base branch name  
BASE_BRANCH="${BASE_BRANCH:-main}"

update_outdated_pr() {
  local pr_number="$1"
  local commits_behind="$2"
  local threshold="${3:-50}"
  local dry_run="${4:-false}"
  
  # Get PR URL and title for logging
  local pr_details pr_url pr_title
  pr_details=$(gh pr view "$pr_number" --json url,title --jq '{url: .url, title: .title}' 2>/dev/null || echo '{"url":"","title":""}')
  pr_url=$(echo "$pr_details" | jq -r '.url')
  pr_title=$(echo "$pr_details" | jq -r '.title' | cut -c1-60)  # Truncate title to 60 chars
  
  if [ "$commits_behind" -le "$threshold" ]; then
    if [[ -n "$pr_url" && "$pr_url" != "null" && -n "$pr_title" && "$pr_title" != "null" ]]; then
      echo "✅ PR #$pr_number ($pr_title) is up-to-date ($commits_behind <= $threshold) - $pr_url" >&2
    else
      echo "✅ PR #$pr_number is up-to-date ($commits_behind <= $threshold), no action needed" >&2
    fi
    return 0
  fi
  
  if [[ -n "$pr_url" && "$pr_url" != "null" && -n "$pr_title" && "$pr_title" != "null" ]]; then
    echo "🚨 PR #$pr_number ($pr_title) is outdated ($commits_behind > $threshold) - $pr_url" >&2
  else
    echo "🚨 PR #$pr_number is outdated ($commits_behind > $threshold)" >&2
  fi
  
  if [[ "$dry_run" == "true" ]]; then
    echo "🔍 DRY-RUN: Would add 'outdated-branch' label and comment to PR #$pr_number" >&2
    echo "📊 DRY-RUN: PR #$pr_number would be marked as outdated ($commits_behind commits behind)" >&2
    return 0
  fi
  
  echo "🚨 Taking action..." >&2
  
  # Check and add label if needed
  local existing_labels
  existing_labels=$(gh pr view "$pr_number" --json labels --jq '.labels[].name' | grep -x "outdated-branch" || true)
  
  if [ -z "$existing_labels" ]; then
    echo "🏷️  Adding outdated-branch label..." >&2
    if ! gh pr edit "$pr_number" --add-label "outdated-branch"; then
      echo "❌ Failed to add label" >&2
    fi
  else
    echo "ℹ️  PR already has outdated-branch label" >&2
  fi
  
  # Check and add comment if needed
  local existing_comment
  existing_comment=$(gh pr view "$pr_number" --json comments --jq '.comments[] | select(.body | contains("significantly behind the '"$BASE_BRANCH"' branch")) | .body' | head -1)
  
  if [ -z "$existing_comment" ]; then
    echo "💬 Adding comment about outdated branch..." >&2
    
    local comment_body
    comment_body=$(cat <<EOF
🚨 **This PR is significantly behind the $BASE_BRANCH branch** 🚨

This PR is currently **$commits_behind commits behind** the $BASE_BRANCH branch. Please update your branch before this PR can be merged.
EOF
)
    
    # Add a comment to notify about outdated status
    if ! gh pr comment "$pr_number" --body "$comment_body"; then
      echo "❌ Failed to add comment" >&2
    fi
  else
    echo "ℹ️  Comment already exists, skipping" >&2
  fi
  
  echo "✅ Updated PR #$pr_number (labeled + commented)" >&2
}
