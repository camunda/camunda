#!/usr/bin/env bash
set -euo pipefail

# Script to find open issues in the camunda repository that are not assigned to any active project
# This helps ensure all issues are triaged and prioritized by the relevant teams

ORG_NAME="camunda"
REPO_NAME="camunda"

echo "Checking for open issues not assigned to any active project in ${ORG_NAME}/${REPO_NAME}..."

# Step 1: Fetch all projects in the repository (both active and archived)
echo "Fetching projects for ${ORG_NAME}/${REPO_NAME}..."

GRAPHQL_QUERY='
  query {
    repository(owner: "'"$ORG_NAME"'", name: "'"$REPO_NAME"'") {
      projectsV2(first: 100) {
        nodes {
          number
          closed
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

# Extract active (open) and archived (closed) project numbers
ACTIVE_PROJECTS=$(echo "$PROJECTS_RESPONSE" | jq -r '.data.repository.projectsV2.nodes[] | select(.closed == false) | .number' | paste -sd "," -)
ARCHIVED_PROJECTS=$(echo "$PROJECTS_RESPONSE" | jq -r '.data.repository.projectsV2.nodes[] | select(.closed == true) | .number' | paste -sd "," -)

# Count projects more robustly
if [[ -z "$ACTIVE_PROJECTS" ]]; then
  ACTIVE_PROJECT_COUNT=0
else
  ACTIVE_PROJECT_COUNT=$(echo "$ACTIVE_PROJECTS" | tr ',' '\n' | grep -c .)
fi

if [[ -z "$ARCHIVED_PROJECTS" ]]; then
  ARCHIVED_PROJECT_COUNT=0
else
  ARCHIVED_PROJECT_COUNT=$(echo "$ARCHIVED_PROJECTS" | tr ',' '\n' | grep -c .)
fi

echo "  Found $ACTIVE_PROJECT_COUNT active project(s)"
echo "  Found $ARCHIVED_PROJECT_COUNT archived project(s)"

# Step 2: Build blended search query for orphaned issues
# We want issues that are:
# - Open
# - Either have no project assignment OR only assigned to archived projects (not to any active project)

# Part 1: Issues with no project assignment
SEARCH_QUERY_NO_PROJECT="repo:${ORG_NAME}/${REPO_NAME} is:issue is:open no:project"

# Part 2: If we have archived projects, build a query for issues assigned to archived projects
# but NOT assigned to any active projects
SEARCH_QUERY_ARCHIVED=""
if [[ -n "$ARCHIVED_PROJECTS" && -n "$ACTIVE_PROJECTS" ]]; then
  IFS=',' read -ra ARCHIVED_ARRAY <<< "$ARCHIVED_PROJECTS"
  IFS=',' read -ra ACTIVE_ARRAY <<< "$ACTIVE_PROJECTS"
  
  # Build OR query for archived projects
  ARCHIVED_OR_PARTS=()
  for archived_proj in "${ARCHIVED_ARRAY[@]}"; do
    ARCHIVED_OR_PARTS+=("project:${ORG_NAME}/${archived_proj}")
  done
  
  # Combine with OR and wrap in parentheses
  ARCHIVED_OR_QUERY=$(IFS=' OR '; echo "${ARCHIVED_OR_PARTS[*]}")
  
  # Add negation for all active projects
  ACTIVE_NEGATIONS=""
  for active_proj in "${ACTIVE_ARRAY[@]}"; do
    ACTIVE_NEGATIONS="${ACTIVE_NEGATIONS} -project:${ORG_NAME}/${active_proj}"
  done
  
  # Complete query for archived project issues
  SEARCH_QUERY_ARCHIVED="repo:${ORG_NAME}/${REPO_NAME} is:issue is:open (${ARCHIVED_OR_QUERY})${ACTIVE_NEGATIONS}"
fi

# Step 3: Count and fetch orphaned issues
echo "Counting orphaned issues..."

# Count and get URL for issues with no project
echo "DEBUG: Searching for issues with query: $SEARCH_QUERY_NO_PROJECT"

if ! RESULT=$(gh api graphql -f query='
  query($searchQuery: String!) {
    search(query: $searchQuery, type: ISSUE, first: 1) {
      issueCount
    }
  }' -F searchQuery="$SEARCH_QUERY_NO_PROJECT" 2>&1); then
  echo "ERROR: Search query failed for no-project issues!"
  echo "Response: $RESULT"
  exit 1
fi

COUNT_NO_PROJECT=$(echo "$RESULT" | jq -r '.data.search.issueCount')
echo "  Issues with no project: $COUNT_NO_PROJECT"

# Generate GitHub search URL for no-project issues
GITHUB_SEARCH_URL=""
if [[ $COUNT_NO_PROJECT -gt 0 ]]; then
  ENCODED_QUERY=$(echo "$SEARCH_QUERY_NO_PROJECT" | jq -sRr @uri)
  GITHUB_SEARCH_URL="https://github.com/${ORG_NAME}/${REPO_NAME}/issues?q=${ENCODED_QUERY}"
  echo "  GitHub search URL: $GITHUB_SEARCH_URL"
fi

# Fetch details for issues in archived projects (if query exists)
ARCHIVED_ISSUES_LIST=""
COUNT_ARCHIVED=0
if [[ -n "$SEARCH_QUERY_ARCHIVED" ]]; then
  echo "  Fetching issues in archived projects..."
  echo "DEBUG: Searching for issues with query: $SEARCH_QUERY_ARCHIVED"
  
  # Fetch up to 50 issues from archived projects
  if ! ARCHIVED_RESULT=$(gh api graphql -f query='
    query($searchQuery: String!) {
      search(query: $searchQuery, type: ISSUE, first: 50) {
        issueCount
        nodes {
          ... on Issue {
            number
            title
            url
          }
        }
      }
    }' -F searchQuery="$SEARCH_QUERY_ARCHIVED" 2>&1); then
    echo "ERROR: Search query failed for archived-project issues!"
    echo "Response: $ARCHIVED_RESULT"
    exit 1
  fi
  
  COUNT_ARCHIVED=$(echo "$ARCHIVED_RESULT" | jq -r '.data.search.issueCount')
  echo "  Issues only in archived projects: $COUNT_ARCHIVED"
  
  # Build list of issues with links
  if [[ $COUNT_ARCHIVED -gt 0 ]]; then
    ARCHIVED_ISSUES_LIST=$(echo "$ARCHIVED_RESULT" | jq -r '.data.search.nodes[] | "• <\(.url)|#\(.number): \(.title)>"' | head -50)
    
    # If there are more than 50, add a note
    if [[ $COUNT_ARCHIVED -gt 50 ]]; then
      ARCHIVED_ISSUES_LIST="${ARCHIVED_ISSUES_LIST}"$'\n'"• _...and $((COUNT_ARCHIVED - 50)) more_"
    fi
  fi
fi

TOTAL_ORPHANED=$((COUNT_NO_PROJECT + COUNT_ARCHIVED))
echo "  Total orphaned: $TOTAL_ORPHANED"

# Step 4: Build Slack message content
SLACK_MESSAGE=""
if [[ $COUNT_NO_PROJECT -gt 0 ]] && [[ $COUNT_ARCHIVED -gt 0 ]]; then
  # Both types of orphaned issues
  SLACK_MESSAGE="*Issues with no project assignment:* $COUNT_NO_PROJECT - <${GITHUB_SEARCH_URL}|View on GitHub>\n\n*Issues only in archived projects:* $COUNT_ARCHIVED\n${ARCHIVED_ISSUES_LIST}"
elif [[ $COUNT_NO_PROJECT -gt 0 ]]; then
  # Only no-project issues
  SLACK_MESSAGE="*Issues with no project assignment:* $COUNT_NO_PROJECT - <${GITHUB_SEARCH_URL}|View on GitHub>"
elif [[ $COUNT_ARCHIVED -gt 0 ]]; then
  # Only archived-project issues
  SLACK_MESSAGE="*Issues only in archived projects:* $COUNT_ARCHIVED\n${ARCHIVED_ISSUES_LIST}"
fi

# Output results to GITHUB_OUTPUT
if [[ $TOTAL_ORPHANED -eq 0 ]]; then
  echo "✅ No orphaned issues found!"
  echo "orphaned-issues-found=0" >> "$GITHUB_OUTPUT"
  echo "orphaned-issues-message=" >> "$GITHUB_OUTPUT"
else
  echo "⚠️  Found $TOTAL_ORPHANED orphaned issue(s)"
  
  # Escape newlines for GitHub Actions output
  SLACK_MESSAGE_ESCAPED="${SLACK_MESSAGE//$'\n'/\\n}"
  
  {
    echo "orphaned-issues-found=$TOTAL_ORPHANED"
    echo "orphaned-issues-message=$SLACK_MESSAGE_ESCAPED"
  } >> "$GITHUB_OUTPUT"
fi
