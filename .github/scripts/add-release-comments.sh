#!/bin/bash

# Script to add release comments to issues that were fixed in recent releases
# This script is called by the add-release-comments-to-issues.yml workflow

set -euo pipefail

CUTOFF_DATE=$(date -d "-${DAYS_BACK} days" --iso-8601)
echo "Looking for releases since: $CUTOFF_DATE"

# Leave headroom under the job's timeout-minutes so an uncapped Retry-After wait
# cannot run us past it. SECONDS counts wall-clock since this script started,
# which is effectively the whole job step.
RUN_DEADLINE=${RUN_DEADLINE_SECONDS:-10200}  # 170m of the 180m job budget

# Post a comment, retrying on GitHub's secondary rate limit for content creation
# (~80/min, ~500/hour). A full-cycle release (RC or minor GA) references ~900
# issues, so a single run necessarily brushes against that ceiling; without this
# the bare POST 403s and `set -e` aborts the whole job mid-run.
post_comment_with_retry() {
  local issue_number="$1" comment_body="$2"
  local attempt=0 max_attempts=8 response http_code retry_after remaining reset_at sleep_for retryable

  while :; do
    attempt=$((attempt + 1))
    # -i keeps the status line and headers so we can honour Retry-After on 403/429.
    if response=$(gh api "repos/$REPOSITORY/issues/$issue_number/comments" \
        -f body="$comment_body" -i 2>&1); then
      return 0
    fi

    http_code=$(printf '%s\n' "$response" | grep -oiE '^HTTP/[0-9.]+ [0-9]+' | grep -oE '[0-9]+$' | head -n1)

    # Only retry failures a wait can actually clear. 429 and 5xx are always
    # transient; a 403 is worth retrying only when it is a rate limit. The
    # x-ratelimit-* headers ride on *every* response (even a plain 404), so they
    # cannot be used as presence signals -- only an explicit Retry-After, an
    # exhausted remaining (0), or a rate-limit/abuse body mean a real limit. A
    # plain permission 403 (or a 404/422) never clears, so failing fast on it
    # keeps one bad issue from burning the entire backoff budget.
    retryable=false
    case "$http_code" in
      429 | 5??) retryable=true ;;
      403)
        if printf '%s\n' "$response" | grep -qiE '^retry-after:' \
          || printf '%s\n' "$response" | grep -qiE '^x-ratelimit-remaining: *0\b' \
          || printf '%s\n' "$response" | grep -qiE 'rate limit|abuse'; then
          retryable=true
        fi
        ;;
    esac

    if [ "$retryable" != true ]; then
      echo "    ✗ Failed to comment on #$issue_number (HTTP ${http_code:-unknown}, not retryable)" >&2
      printf '%s\n' "$response" >&2
      return 1
    fi
    if [ "$attempt" -ge "$max_attempts" ]; then
      echo "    ✗ Giving up on #$issue_number after $attempt attempts (still failing, last HTTP $http_code)" >&2
      return 1
    fi

    # Honour the server's own hint uncapped: GitHub's docs say not to retry
    # before Retry-After elapses, and an exhausted primary limit's reset window
    # can legitimately exceed a few minutes. Only the fallback backoff (no hint
    # given) is capped.
    retry_after=$(printf '%s\n' "$response" | grep -oiE '^retry-after: *[0-9]+' | grep -oE '[0-9]+$' | head -n1)
    remaining=$(printf '%s\n' "$response" | grep -oiE '^x-ratelimit-remaining: *[0-9]+' | grep -oE '[0-9]+$' | head -n1)
    reset_at=$(printf '%s\n' "$response" | grep -oiE '^x-ratelimit-reset: *[0-9]+' | grep -oE '[0-9]+$' | head -n1)
    if [ -n "$retry_after" ]; then
      sleep_for="$retry_after"
    elif [ "$remaining" = "0" ] && [ -n "$reset_at" ]; then
      sleep_for=$((reset_at - $(date +%s) + 1))
    else
      sleep_for=$((60 * 2 ** (attempt - 1)))
      [ "$sleep_for" -gt 900 ] && sleep_for=900
    fi
    [ "$sleep_for" -lt 1 ] && sleep_for=1

    # A large (now uncapped) wait must not sleep past the job budget; give up and
    # let the end-of-run failure surface it for a re-run instead.
    if [ $((SECONDS + sleep_for)) -gt "$RUN_DEADLINE" ]; then
      echo "    ✗ Giving up on #$issue_number: waiting ${sleep_for}s would overrun the job budget" >&2
      return 1
    fi
    echo "    Retrying #$issue_number in ${sleep_for}s (attempt $attempt/$max_attempts, HTTP $http_code)" >&2
    sleep "$sleep_for"
  done
}

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

# Track issues we could not comment on, so the run fails loudly at the end
# instead of reporting success with silent gaps.
FAILED_ISSUES=""

# Process each release. Feed the loop via process substitution rather than a
# pipe, so FAILED_ISSUES accumulates in this shell and survives the loop.
while read -r release; do
  TAG_NAME=$(echo "$release" | jq -r '.tag_name')
  RELEASE_URL=$(echo "$release" | jq -r '.html_url')
  PUBLISHED_AT=$(echo "$release" | jq -r '.published_at')
  RELEASE_BODY=$(echo "$release" | jq -r '.body')
  
  echo ""
  echo "Processing release: $TAG_NAME ($PUBLISHED_AT)"
  echo "Release URL: $RELEASE_URL"

  # Derive major.minor version for Helm chart release notes URL (e.g. 8.6 from 8.6.4 or v8.6.4)
  MAJOR_MINOR_VERSION=$(echo "$TAG_NAME" | grep -oE '[0-9]+\.[0-9]+' | head -n 1 || true)
  HELM_RELEASE_NOTES_URL=""
  if [ -n "$MAJOR_MINOR_VERSION" ]; then
    HELM_RELEASE_NOTES_URL="https://helm.camunda.io/camunda-platform/version-matrix/camunda-$MAJOR_MINOR_VERSION/"
    echo "Helm chart release notes URL: $HELM_RELEASE_NOTES_URL"
  else
    echo "Could not derive major.minor from tag '$TAG_NAME'; skipping Helm chart release notes link"
  fi
  
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

    # Skip issues closed as not planned (this also covers "closed as duplicate",
    # since the GitHub REST API reports that as state_reason "not_planned" too)
    STATE_REASON=$(echo "$ISSUE_INFO" | jq -r '.state_reason // empty')
    if [ "$STATE_REASON" = "not_planned" ]; then
      echo "    Skipping #$ISSUE_NUMBER - closed as $STATE_REASON"
      continue
    fi

    # Check if release comment already exists
    COMMENT_EXISTS=$(gh api "repos/$REPOSITORY/issues/$ISSUE_NUMBER/comments?per_page=100" \
      --paginate \
      --jq ".[] | select(.body | contains(\"This has been released in version [$TAG_NAME]\")) | .id" | head -n 1 || true)
    
    if [ -n "$COMMENT_EXISTS" ]; then
      echo "    Skipping #$ISSUE_NUMBER - release comment already exists"
      continue
    fi
    
    # Prepare comment
    COMMENT_BODY="This has been released in version [$TAG_NAME]($RELEASE_URL). See release notes [here]($RELEASE_URL) for details."
    if [ -n "$HELM_RELEASE_NOTES_URL" ]; then
      COMMENT_BODY="$COMMENT_BODY Helm chart release notes are available [here]($HELM_RELEASE_NOTES_URL)."
    fi
    
    if [ "$DRY_RUN" = "true" ]; then
      echo "    [DRY RUN] Would add comment to issue #$ISSUE_NUMBER:"
      echo "    Comment: $COMMENT_BODY"
    else
      echo "    Adding comment to issue #$ISSUE_NUMBER"
      if post_comment_with_retry "$ISSUE_NUMBER" "$COMMENT_BODY"; then
        echo "    ✓ Comment added successfully"
      else
        echo "    ✗ Skipped #$ISSUE_NUMBER after repeated failures"
        FAILED_ISSUES="$FAILED_ISSUES #$ISSUE_NUMBER"
      fi
    fi
  done
done < <(echo "$RELEASES" | jq -c '.')

echo ""
echo "Release comment processing completed."

# A rate-limit give-up or hard error leaves an issue uncommented. Surface it as a
# job failure: the weekly re-run only recovers it while the release is still
# inside DAYS_BACK, so a green run here would hide a permanent gap.
if [ -n "$FAILED_ISSUES" ]; then
  echo "::error::Could not comment on:$FAILED_ISSUES. Re-run the workflow (raise days_back if the release has aged out); already-commented issues are skipped." >&2
  exit 1
fi
