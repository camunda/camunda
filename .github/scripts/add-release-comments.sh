#!/bin/bash

# Script to add release comments to issues that were fixed in recent releases
# This script is called by the add-release-comments-to-issues.yml workflow

set -euo pipefail

CUTOFF_DATE=$(date -d "-${DAYS_BACK} days" --iso-8601)
echo "Looking for releases since: $CUTOFF_DATE"

# Fetch releases from GitHub API
RELEASES=$(gh api repos/$REPOSITORY/releases \
  --jq ".[] | select(.published_at > \"$CUTOFF_DATE\") | {tag_name, html_url, published_at, body}" \
  --paginate)

if [ -z "$RELEASES" ]; then
  echo "No releases found since $CUTOFF_DATE"
  exit 0
fi

echo "Found releases:"
echo "$RELEASES" | jq -r '.tag_name + " - " + .published_at'

# Process each release
echo "$RELEASES" | jq -c '.' | while read -r release; do
  TAG_NAME=$(echo "$release" | jq -r '.tag_name')
  RELEASE_URL=$(echo "$release" | jq -r '.html_url')
  PUBLISHED_AT=$(echo "$release" | jq -r '.published_at')
  RELEASE_BODY=$(echo "$release" | jq -r '.body')
  
  echo ""
  echo "Processing release: $TAG_NAME ($PUBLISHED_AT)"
  echo "Release URL: $RELEASE_URL"
  
  # Extract issue numbers from release body - patterns: #1234, owner/repo#1234, [#1234], github.com/owner/repo/issues/1234
  ISSUE_NUMBERS=$(echo "$RELEASE_BODY" | \
    grep -oE '(\#|issues?[/#]|issue[/#]|'$REPOSITORY'\#)[0-9]+|\[#[0-9]+\]|github\.com/'$REPOSITORY'/(issues|pull)/[0-9]+' | \
    grep -oE '[0-9]+' | \
    sort -u || true)
  
  if [ -z "$ISSUE_NUMBERS" ]; then
    echo "No issue references found in release $TAG_NAME"
    continue
  fi
  
  echo "Found issue references: $(echo "$ISSUE_NUMBERS" | tr '\n' ' ')"
  
  # Process each issue
  for ISSUE_NUMBER in $ISSUE_NUMBERS; do
    echo "  Checking issue #$ISSUE_NUMBER..."
    
    # Get issue details
    ISSUE_INFO=$(gh api repos/$REPOSITORY/issues/$ISSUE_NUMBER 2>/dev/null || true)
    
    if [ -z "$ISSUE_INFO" ]; then
      echo "    Issue #$ISSUE_NUMBER not found or not accessible"
      continue
    fi
    
    # Skip PRs
    IS_PR=$(echo "$ISSUE_INFO" | jq -r '.pull_request // empty')
    if [ -n "$IS_PR" ]; then
      echo "    Skipping #$ISSUE_NUMBER - it's a pull request"
      continue
    fi
    
    # Skip open issues
    STATE=$(echo "$ISSUE_INFO" | jq -r '.state')
    if [ "$STATE" != "closed" ]; then
      echo "    Skipping #$ISSUE_NUMBER - issue is not closed (state: $STATE)"
      continue
    fi
    
    # Check if release comment already exists
    COMMENT_EXISTS=$(gh api repos/$REPOSITORY/issues/$ISSUE_NUMBER/comments \
      --jq ".[] | select(.body | contains(\"This has been released in version [$TAG_NAME]\")) | .id" | head -n 1 || true)
    
    if [ -n "$COMMENT_EXISTS" ]; then
      echo "    Skipping #$ISSUE_NUMBER - release comment already exists"
      continue
    fi
    
    # Prepare comment
    COMMENT_BODY="This has been released in version [$TAG_NAME]($RELEASE_URL). See release notes [here]($RELEASE_URL) for details."
    
    if [ "$DRY_RUN" = "true" ]; then
      echo "    [DRY RUN] Would add comment to issue #$ISSUE_NUMBER:"
      echo "    Comment: $COMMENT_BODY"
    else
      echo "    Adding comment to issue #$ISSUE_NUMBER"
      gh api repos/$REPOSITORY/issues/$ISSUE_NUMBER/comments \
        -f body="$COMMENT_BODY" > /dev/null
      echo "    ✓ Comment added successfully"
    fi
  done
done

echo ""
echo "Release comment processing completed."