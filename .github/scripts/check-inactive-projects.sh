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

GRAPHQL_QUERY='
  query {
    repository(owner: "'"$ORG_NAME"'", name: "'"$REPO_NAME"'") {
      projectsV2(first: 100) {
        nodes {
          id
          number
          title
          url
          closed
          items(first: 100) {
            nodes {
              updatedAt
              isArchived
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
  }'

echo "DEBUG: Executing GraphQL query:"
echo "$GRAPHQL_QUERY"

# Execute query and capture both stdout and stderr
if ! PROJECTS_RESPONSE=$(gh api graphql -f query="$GRAPHQL_QUERY" 2>&1); then
  echo "ERROR: GraphQL query failed!"
  echo "Response: $PROJECTS_RESPONSE"
  exit 1
fi

echo "DEBUG: GraphQL query successful"
echo "DEBUG: Response length: ${#PROJECTS_RESPONSE} characters"

# Use a temp file to collect results (needed because while loop runs in a subshell)
TEMP_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE"' EXIT

# Process each project
echo "$PROJECTS_RESPONSE" | jq -c '.data.repository.projectsV2.nodes[] | select(.closed == false)' | while read -r project; do
  PROJECT_NUMBER=$(echo "$project" | jq -r '.number')
  PROJECT_TITLE=$(echo "$project" | jq -r '.title')
  PROJECT_URL=$(echo "$project" | jq -r '.url')
  
  echo "  Checking project #$PROJECT_NUMBER: $PROJECT_TITLE"
  
  # Get the most recently updated open item (filtering out archived items)
  # We fetch both issues and PRs
  MOST_RECENT_UPDATE=$(echo "$project" | jq -r '
    [.items.nodes[] | 
     select(.isArchived == false) | 
     select(.content.state == "OPEN") | 
     .updatedAt
    ] | 
    sort | 
    last // empty
  ')
  
  # Count open non-archived items
  OPEN_ITEMS_COUNT=$(echo "$project" | jq -r '
    [.items.nodes[] | 
     select(.isArchived == false) | 
     select(.content.state == "OPEN")
    ] | length
  ')
  
  # If there are no open items, the project has no open items
  if [[ "$OPEN_ITEMS_COUNT" == "0" ]]; then
    echo "    → No open items found"
    UPDATE_INFO="No open items"
    echo "• <${PROJECT_URL}|Project #${PROJECT_NUMBER}: ${PROJECT_TITLE}> - ${UPDATE_INFO}" >> "$TEMP_FILE"
    continue
  fi
  
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
