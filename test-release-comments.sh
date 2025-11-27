#!/bin/bash

# Test script for the release comment workflow
# This script can be run manually to test the logic without actually posting comments

set -euo pipefail

# Override for testing - set to a smaller number to test recent releases
DAYS_BACK=${1:-7}
DRY_RUN=true

echo "Testing release comment script with DAYS_BACK=$DAYS_BACK and DRY_RUN=$DRY_RUN"

# Calculate the date to look back from
CUTOFF_DATE=$(date -d "-${DAYS_BACK} days" --iso-8601)
echo "Looking for releases since: $CUTOFF_DATE"

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is required but not installed"
    echo "Please install it from: https://cli.github.com/"
    exit 1
fi

# Check if we're authenticated
if ! gh auth status &> /dev/null; then
    echo "Error: Not authenticated with GitHub CLI"
    echo "Please run: gh auth login"
    exit 1
fi

# Test repository detection
REPO=$(gh repo view --json nameWithOwner -q '.nameWithOwner' 2>/dev/null || echo "camunda/camunda")
echo "Using repository: $REPO"

# Fetch releases from GitHub API
echo "Fetching recent releases..."
RELEASES=$(gh api repos/$REPO/releases \
  --jq ".[] | select(.published_at > \"$CUTOFF_DATE\") | {tag_name, html_url, published_at, body}" \
  --paginate)

if [ -z "$RELEASES" ]; then
  echo "No releases found since $CUTOFF_DATE"
  exit 0
fi

echo "Found releases:"
echo "$RELEASES" | jq -r '.tag_name + " - " + .published_at'

# Process each release (limit to first 2 for testing)
echo "$RELEASES" | jq -c '.' | head -2 | while read -r release; do
  TAG_NAME=$(echo "$release" | jq -r '.tag_name')
  RELEASE_URL=$(echo "$release" | jq -r '.html_url')
  PUBLISHED_AT=$(echo "$release" | jq -r '.published_at')
  RELEASE_BODY=$(echo "$release" | jq -r '.body')
  
  echo ""
  echo "Processing release: $TAG_NAME ($PUBLISHED_AT)"
  echo "Release URL: $RELEASE_URL"
  
  # Extract issue numbers from release body
  ISSUE_NUMBERS=$(echo "$RELEASE_BODY" | \
    grep -oE '(\#|issues?[/#]|issue[/#]|camunda/camunda\#)[0-9]+|\[#[0-9]+\]|github\.com/camunda/camunda/(issues|pull)/[0-9]+' | \
    grep -oE '[0-9]+' | \
    sort -u | head -5 || true)
  
  if [ -z "$ISSUE_NUMBERS" ]; then
    echo "No issue references found in release $TAG_NAME"
    continue
  fi
  
  echo "Found issue references: $(echo "$ISSUE_NUMBERS" | tr '\n' ' ')"
  
  # Process each issue (limit to first 3 for testing)
  echo "$ISSUE_NUMBERS" | head -3 | while read -r ISSUE_NUMBER; do
    echo "  Checking issue #$ISSUE_NUMBER..."
    
    # Get issue details to verify it's an issue (not a PR)
    ISSUE_INFO=$(gh api repos/$REPO/issues/$ISSUE_NUMBER 2>/dev/null || true)
    
    if [ -z "$ISSUE_INFO" ]; then
      echo "    Issue #$ISSUE_NUMBER not found or not accessible"
      continue
    fi
    
    # Check if this is a pull request
    IS_PR=$(echo "$ISSUE_INFO" | jq -r '.pull_request // empty')
    if [ -n "$IS_PR" ]; then
      echo "    Skipping #$ISSUE_NUMBER - it's a pull request"
      continue
    fi
    
    # Check if the issue is closed
    STATE=$(echo "$ISSUE_INFO" | jq -r '.state')
    if [ "$STATE" != "closed" ]; then
      echo "    Skipping #$ISSUE_NUMBER - issue is not closed (state: $STATE)"
      continue
    fi
    
    # Prepare the comment
    COMMENT_BODY="This has been released in version [$TAG_NAME]($RELEASE_URL). See release notes [here]($RELEASE_URL) for details."
    
    echo "    [TEST] Would add comment to issue #$ISSUE_NUMBER:"
    echo "    Comment: $COMMENT_BODY"
    
    # Show issue title for context
    ISSUE_TITLE=$(echo "$ISSUE_INFO" | jq -r '.title')
    echo "    Issue title: $ISSUE_TITLE"
  done
done

echo ""
echo "Test completed successfully!"