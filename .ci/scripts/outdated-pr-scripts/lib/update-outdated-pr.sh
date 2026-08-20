#!/bin/bash
# Update outdated PR with label and comment

update_outdated_pr() {
  local pr_number="$1"
  local commits_behind="$2"
  local base_branch="$3"
  local threshold="${4:-50}"
  local latest_age_days="${5:-0}"
  local stale_threshold="${6:-7}"
  local dry_run="${7:-true}"
  
  # Get all PR data in one call and extract what we need
  local pr_data existing_labels existing_comment_id existing_comment_body
  pr_data=$(gh pr view "$pr_number" --json title,labels,comments,url)
  existing_labels=$(echo "$pr_data" | jq -r '.labels[] | select(.name == "outdated-branch") | .name' || true)
  existing_comment_id=$(echo "$pr_data" | jq -r '.comments[] | select(.body | contains("🚨") and (contains("last updated") or contains("commits behind"))) | .url' | head -1 | sed 's/.*#issuecomment-//' || true)
  existing_comment_body=$(echo "$pr_data" | jq -r '.comments[] | select(.body | contains("🚨") and (contains("last updated") or contains("commits behind"))) | .body' | head -1 || true)
  
  if [[ "$dry_run" == "true" ]]; then
    echo "🔍 DRY-RUN: Would add 'outdated-branch' label and comment to PR #$pr_number"
    return 0
  fi
  
  # Check and add label if needed
  if [ -z "$existing_labels" ]; then
    echo "🏷️  Adding outdated-branch label..."
    if ! gh pr edit "$pr_number" --add-label "outdated-branch"; then
      echo "❌ Failed to add label" >&2
    fi
  else
    echo "ℹ️  PR already has outdated-branch label"
  fi
  
  # Check and add/update comment
  local comment_body details=""
  
  # Build detailed status message
  if [[ "$commits_behind" -gt "$threshold" ]]; then
    details="**$commits_behind commits behind** the $base_branch branch (threshold: $threshold)"
  fi
  
  if [[ "$latest_age_days" -gt "$stale_threshold" ]]; then
    if [[ -n "$details" ]]; then
      details="$details and **last updated $latest_age_days days ago** (threshold: $stale_threshold days)"
    else
      details="**last updated $latest_age_days days ago** (threshold: $stale_threshold days)"
    fi
  fi
  
  comment_body=$(cat <<EOF
🚨 **This PR needs attention** 🚨

This PR is currently $details. Please update your branch before this PR can be merged.
EOF
)
  
  if [[ -n "$existing_comment_id" && "$existing_comment_id" != "null" ]]; then
    # Update existing comment if content changed
    if [[ "$existing_comment_body" != "$comment_body" ]]; then
      echo "💬 Updating existing comment with new conditions... (Comment ID: $existing_comment_id)"
      if gh api /repos/"$GITHUB_REPOSITORY"/issues/comments/"$existing_comment_id" -X PATCH -f body="$comment_body" >/dev/null 2>&1; then
        echo "✅ Successfully updated comment"
      else
        echo "❌ Failed to update PR comment"
      fi
    else
      echo "ℹ️  Comment is already up-to-date"
    fi
  else
    # Create new comment since PR is outdated and no comment exists
    echo "💬 Adding new comment about outdated branch..."
    if ! gh pr comment "$pr_number" --body "$comment_body"; then
      echo "❌ Failed to add comment" >&2
    fi
  fi
  
  echo "🔧 Updated PR #$pr_number (labeled + commented)"
}
