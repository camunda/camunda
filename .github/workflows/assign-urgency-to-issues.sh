#!/usr/bin/env bash
set -euo pipefail

# --- Configuration with CLI arguments and fallbacks ---
ORG_NAME="camunda"                                    # Hardcoded organization
REPO_NAME="camunda"                                   # Hardcoded repository
PROJECT_ID="${1:-173}"                                # Numeric Project V2 number
BRANCH="${2:-main}"        # Branch to trigger workflow on
LIMIT="${3:-20}"                                      # Number of issues to process
SKIP_ASSIGNED="${4:-false}"                           # Skip issues that already have urgency assigned (true/false)
DRY_RUN="${5:-false}"                                 # Dry run mode - don't trigger workflows (true/false)

# Display usage if help is requested
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<EOF
Usage: $0 [PROJECT_ID] [BRANCH] [LIMIT] [SKIP_ASSIGNED] [DRY_RUN]

Arguments (all optional, with defaults):
  PROJECT_ID       Numeric Project V2 number (default: 173)
  BRANCH           Branch to trigger workflow on (default: eppdot-urgency-field-automation)
  LIMIT            Number of issues to process (default: 20)
  SKIP_ASSIGNED    Skip issues that already have urgency assigned (default: false)
  DRY_RUN          Don't trigger workflows, just show what would be done (default: false)

Examples:
  $0                        # Use all defaults - process all issues in project 173
  $0 173                    # Specify project
  $0 173 main 50            # All parameters except skip filter and dry run
  $0 173 main 50 true       # Only process issues without urgency
  $0 173 main 50 true true  # Dry run - show what would be processed
EOF
  exit 0
fi

# --- Fetch first N issues from the project ---
echo "Fetching up to $LIMIT issues from project (with pagination)..."

# Initialize variables for pagination
ISSUE_NUMBERS=""
ISSUE_COUNT=0
CURSOR="null"
PAGE_SIZE=50
MAX_PAGES=20  # Safety limit to avoid infinite loops

# Build search query - search for open issues in the repo and project
SEARCH_QUERY="repo:${ORG_NAME}/${REPO_NAME} is:issue is:open project:${ORG_NAME}/${PROJECT_ID}"

for ((page=1; page<=MAX_PAGES; page++)); do
  # Build query with pagination cursor
  if [[ "$CURSOR" == "null" ]]; then
    CURSOR_ARG=""
  else
    CURSOR_ARG=", after: \"$CURSOR\""
  fi

  # Search for issues - project filtering is done server-side via search query
  RESPONSE=$(gh api graphql -f query="
    query(\$searchQuery: String!) {
      search(query: \$searchQuery, type: ISSUE, first: $PAGE_SIZE$CURSOR_ARG) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          ... on Issue {
            number
            projectItems(first: 5) {
              nodes {
                fieldValues(first: 20) {
                  nodes {
                    ... on ProjectV2ItemFieldSingleSelectValue {
                      field {
                        ... on ProjectV2SingleSelectField {
                          name
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }" -F searchQuery="$SEARCH_QUERY")

  # Optionally filter out issues that already have urgency assigned
  if [[ "$SKIP_ASSIGNED" == "true" ]]; then
    PAGE_ISSUES=$(echo "$RESPONSE" | jq -r '
      .data.search.nodes[]
      | select(.projectItems.nodes[0].fieldValues.nodes | map(select(.field.name == "Urgency")) | length == 0)
      | .number')
  else
    PAGE_ISSUES=$(echo "$RESPONSE" | jq -r '.data.search.nodes[].number')
  fi

  # Add issues from this page
  if [[ -n "$PAGE_ISSUES" ]]; then
    if [[ -z "$ISSUE_NUMBERS" ]]; then
      ISSUE_NUMBERS="$PAGE_ISSUES"
    else
      ISSUE_NUMBERS="$ISSUE_NUMBERS"$'\n'"$PAGE_ISSUES"
    fi
    ISSUE_COUNT=$(echo "$ISSUE_NUMBERS" | wc -l | tr -d ' ')
  fi

  echo "  Page $page: Found $ISSUE_COUNT issue(s) so far..."

  # Check if we have enough issues
  if [[ $ISSUE_COUNT -ge $LIMIT ]]; then
    echo "  Reached target of $LIMIT issues"
    ISSUE_NUMBERS=$(echo "$ISSUE_NUMBERS" | head -n "$LIMIT")
    break
  fi

  # Check if there are more pages
  HAS_NEXT=$(echo "$RESPONSE" | jq -r '.data.search.pageInfo.hasNextPage')
  if [[ "$HAS_NEXT" != "true" ]]; then
    echo "  No more pages available"
    break
  fi

  # Get cursor for next page
  CURSOR=$(echo "$RESPONSE" | jq -r '.data.search.pageInfo.endCursor')
done

if [[ -z "$ISSUE_NUMBERS" ]]; then
  echo "⚠️  No issues found in the project (or missing permissions)."
  exit 1
fi

ISSUE_COUNT=$(echo "$ISSUE_NUMBERS" | wc -l | tr -d ' ')
echo "✅ Found $ISSUE_COUNT issue(s) to process"

# --- Run the workflow for each issue ---
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔍 DRY RUN MODE - Would trigger workflow for these issues:"
  for ISSUE_NUMBER in $ISSUE_NUMBERS; do
    echo "  → Would run for issue #$ISSUE_NUMBER"
  done
  echo "ℹ️  Dry run complete. Would have triggered workflow for $ISSUE_COUNT issue(s)."
else
  echo "Running workflow 'assign-urgency-to-issue.yml' for issues:"
  for ISSUE_NUMBER in $ISSUE_NUMBERS; do
    echo "→ Running for issue #$ISSUE_NUMBER"
    gh workflow run assign-urgency-to-issue.yml \
      --ref "$BRANCH" \
      -f issue_number="$ISSUE_NUMBER" \
      -f project_id="$PROJECT_ID"
  done
  echo "✅ Done! Triggered workflow for $ISSUE_COUNT issue(s)."
fi
