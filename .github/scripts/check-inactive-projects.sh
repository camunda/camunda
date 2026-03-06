#!/usr/bin/env bash
set -euo pipefail

# Script to find open projects in the camunda repository where no open items have been updated in the last 30 days
# This helps identify projects that may be complete and should be archived

ORG_NAME="camunda"
REPO_NAME="camunda"
DAYS_THRESHOLD=30
# Note: Using GNU date syntax (requires ubuntu-latest runner)
CUTOFF_DATE=$(date -u -d "30 days ago" +%Y-%m-%dT%H:%M:%SZ)

echo "Checking for open projects with no item updates in the last $DAYS_THRESHOLD days..."
echo "Cutoff date: $CUTOFF_DATE"

# Get all open (non-archived) projects for the repository
echo "Fetching projects for ${ORG_NAME}/${REPO_NAME}..."

GRAPHQL_QUERY='
  query {
    repository(owner: "'"$ORG_NAME"'", name: "'"$REPO_NAME"'") {
      projectsV2(first: 100, query: "is:open") {
        nodes {
          id
          number
          title
          url
          closed
          items(first: 1, query: "is:open updated:>@today-'"$DAYS_THRESHOLD"'d", orderBy: {field: POSITION, direction: ASC}) {
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
# Note: Since we use query: "is:open" on projectsV2, we only get open projects
echo "$PROJECTS_RESPONSE" | jq -c '.data.repository.projectsV2.nodes[]' | while read -r project; do
  PROJECT_NUMBER=$(echo "$project" | jq -r '.number')
  PROJECT_TITLE=$(echo "$project" | jq -r '.title')
  PROJECT_URL=$(echo "$project" | jq -r '.url')
  
  echo "  Checking project #$PROJECT_NUMBER: $PROJECT_TITLE"
  
  # Count items returned (these are items updated in the last 30 days)
  RECENT_ITEMS_COUNT=$(echo "$project" | jq -r '.items.nodes | length')
  
  # If no items were returned, the project has no recent activity
  if [[ "$RECENT_ITEMS_COUNT" == "0" ]]; then
    echo "    → No items updated in the last $DAYS_THRESHOLD days"
    UPDATE_INFO="No updates in the last $DAYS_THRESHOLD days"
    echo "• <${PROJECT_URL}|Project #${PROJECT_NUMBER}: ${PROJECT_TITLE}> - ${UPDATE_INFO}" >> "$TEMP_FILE"
  else
    echo "    → Has items updated in the last $DAYS_THRESHOLD days"
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
