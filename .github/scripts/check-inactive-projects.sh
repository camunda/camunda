#!/usr/bin/env bash
set -euo pipefail

# Script to find open projects in the camunda repository where no issues have had status changes in the last 30 days
# This helps identify projects that may be complete and should be archived

ORG_NAME="camunda"
REPO_NAME="camunda"
DAYS_THRESHOLD=30
# Note: Using GNU date syntax (requires ubuntu-latest runner)
CUTOFF_DATE=$(date -u -d "30 days ago" +%Y-%m-%dT%H:%M:%SZ)

echo "Checking for open projects with no status changes in the last $DAYS_THRESHOLD days..."
echo "Cutoff date: $CUTOFF_DATE"

# Get all open (non-archived) projects for the repository
echo "Fetching projects for ${ORG_NAME}/${REPO_NAME}..."

PROJECTS_RESPONSE=$(gh api graphql -f query="
  query {
    repository(owner: \"$ORG_NAME\", name: \"$REPO_NAME\") {
      projectsV2(first: 100) {
        nodes {
          id
          number
          title
          url
          closed
          items(first: 1, orderBy: {field: UPDATED_AT, direction: DESC}, filterBy: {excludeArchived: true}) {
            nodes {
              updatedAt
              content {
                ... on Issue {
                  __typename
                  state
                }
                ... on PullRequest {
                  __typename
                  state
                }
              }
            }
          }
        }
      }
    }
  }")

# Use a temp file to collect results (needed because while loop runs in a subshell)
TEMP_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE"' EXIT

# Process each project
echo "$PROJECTS_RESPONSE" | jq -c '.data.repository.projectsV2.nodes[] | select(.closed == false)' | while read -r project; do
  PROJECT_NUMBER=$(echo "$project" | jq -r '.number')
  PROJECT_TITLE=$(echo "$project" | jq -r '.title')
  PROJECT_URL=$(echo "$project" | jq -r '.url')
  
  echo "  Checking project #$PROJECT_NUMBER: $PROJECT_TITLE"
  
  # Get the most recently updated item (already sorted by UPDATED_AT DESC)
  # We fetch both issues and PRs (filterBy excludes archived items)
  FIRST_ITEM=$(echo "$project" | jq -r '.items.nodes[0] // empty')
  
  # If there are no items, the project has no open items
  if [[ -z "$FIRST_ITEM" ]]; then
    echo "    → No open items found"
    UPDATE_INFO="No open items"
    echo "• <${PROJECT_URL}|Project #${PROJECT_NUMBER}: ${PROJECT_TITLE}> - ${UPDATE_INFO}" >> "$TEMP_FILE"
    continue
  fi
  
  # Check if it's an open item (issue or PR)
  IS_OPEN=$(echo "$FIRST_ITEM" | jq -r 'select(.content.state == "OPEN") | "true"')
  
  if [[ "$IS_OPEN" != "true" ]]; then
    # The most recent item is not open, so project has no open items
    echo "    → No open items found"
    UPDATE_INFO="No open items"
    echo "• <${PROJECT_URL}|Project #${PROJECT_NUMBER}: ${PROJECT_TITLE}> - ${UPDATE_INFO}" >> "$TEMP_FILE"
    continue
  fi
  
  # Get the update timestamp of the most recently updated open item (issue or PR)
  MOST_RECENT_UPDATE=$(echo "$FIRST_ITEM" | jq -r '.updatedAt')
  
  # If no update found or the most recent is older than threshold
  if [[ -z "$MOST_RECENT_UPDATE" ]] || [[ "$MOST_RECENT_UPDATE" < "$CUTOFF_DATE" ]]; then
    if [[ -z "$MOST_RECENT_UPDATE" ]]; then
      echo "    → No updates found"
      UPDATE_INFO="No updates"
    else
      echo "    → Last update: $MOST_RECENT_UPDATE (older than threshold)"
      # Try to format the date (GNU date syntax, works on ubuntu-latest)
      # Fall back to generic message if parsing fails
      if UPDATE_DATE=$(date -d "$MOST_RECENT_UPDATE" "+%Y-%m-%d" 2>/dev/null); then
        UPDATE_INFO="Last updated: $UPDATE_DATE"
      else
        UPDATE_INFO="Last updated: over 30 days ago"
      fi
    fi
    
    echo "• <${PROJECT_URL}|Project #${PROJECT_NUMBER}: ${PROJECT_TITLE}> - ${UPDATE_INFO}" >> "$TEMP_FILE"
  else
    echo "    → Last update: $MOST_RECENT_UPDATE (within threshold)"
  fi
done

# Read results from temp file
INACTIVE_PROJECTS=""
INACTIVE_COUNT=0
if [[ -s "$TEMP_FILE" ]]; then
  INACTIVE_PROJECTS=$(cat "$TEMP_FILE")
  INACTIVE_COUNT=$(wc -l < "$TEMP_FILE" | tr -d ' ')
fi

# Output results to GITHUB_OUTPUT
if [[ -z "$INACTIVE_PROJECTS" ]]; then
  echo "✅ No inactive projects found!"
  echo "inactive-projects-found=0" >> "$GITHUB_OUTPUT"
  echo "inactive-projects-list=" >> "$GITHUB_OUTPUT"
else
  echo "⚠️  Found $INACTIVE_COUNT potentially inactive project(s)"
  
  # Escape newlines for GitHub Actions output
  INACTIVE_PROJECTS_ESCAPED="${INACTIVE_PROJECTS//$'\n'/\\n}"
  
  {
    echo "inactive-projects-found=$INACTIVE_COUNT"
    echo "inactive-projects-list=$INACTIVE_PROJECTS_ESCAPED"
  } >> "$GITHUB_OUTPUT"
fi
