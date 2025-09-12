#!/bin/bash
# Update outdated PR with label and comment

update_outdated_pr() {
  local pr_number="$1"
  local commits_behind="$2"
  local base_branch="$3"
  local threshold="${4:-50}"
  local dry_run="${5:-false}"
  
  # Get all PR data in one call and extract what we need
  local pr_data pr_title existing_labels existing_comment
  pr_data=$(gh pr view "$pr_number" --json title,labels,comments)
  pr_title=$(echo "$pr_data" | jq -r '.title[0:60]')
  existing_labels=$(echo "$pr_data" | jq -r '.labels[] | select(.name == "outdated-branch") | .name' || true)
  existing_comment=$(echo "$pr_data" | jq -r '.comments[] | select(.body | contains("significantly behind the '"$base_branch"' branch")) | .body' | head -1 || true)
  
  if [ "$commits_behind" -le "$threshold" ]; then
    echo "✅ PR #$pr_number ($pr_title) is up-to-date ($commits_behind <= $threshold)" >&2
    return 0
  fi
  
  echo "🚨 PR #$pr_number ($pr_title) is outdated ($commits_behind > $threshold)" >&2
  
  if [[ "$dry_run" == "true" ]]; then
    echo "🔍 DRY-RUN: Would add 'outdated-branch' label and comment to PR #$pr_number" >&2
    return 0
  fi
  
  # Check and add label if needed
  if [ -z "$existing_labels" ]; then
    echo "🏷️  Adding outdated-branch label..." >&2
    if ! gh pr edit "$pr_number" --add-label "outdated-branch"; then
      echo "❌ Failed to add label" >&2
    fi
  else
    echo "ℹ️  PR already has outdated-branch label" >&2
  fi
  
  # Check and add comment if needed
  
  if [ -z "$existing_comment" ]; then
    echo "💬 Adding comment about outdated branch..." >&2
    
    local comment_body
    comment_body=$(cat <<EOF
🚨 **This PR is significantly behind the $base_branch branch** 🚨

This PR is currently **$commits_behind commits behind** the $base_branch branch. Please update your branch before this PR can be merged.
EOF
)
    
    # Add a comment to notify about outdated status
    if ! gh pr comment "$pr_number" --body "$comment_body"; then
      echo "❌ Failed to add comment" >&2
    fi
  else
    echo "ℹ️  Comment already exists, skipping" >&2
  fi
  
  echo "🔧 Updated PR #$pr_number (labeled + commented)" >&2
}
