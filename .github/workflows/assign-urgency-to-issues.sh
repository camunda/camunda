#!/usr/bin/env bash
set -euo pipefail

# --- Constants ---
ORG_NAME="camunda"

# --- Defaults ---
REPO_NAME="camunda"
PROJECT_ID="173"
BRANCH="main"
LIMIT=20
SKIP_ASSIGNED=false
DRY_RUN=false
ISSUE_TYPE=""
LABEL=""
DELAY=0
UPDATED_SINCE=""

URGENCY_FIELD_ID="IFSS_kgDNKAw"

# --- Parse flags ---
usage() {
  cat <<EOF
Usage: $0 [OPTIONS]

Batch-trigger the urgency automation workflow for issues in a project.

Options:
  --repo <name>        Repository name (default: camunda)
  --project <id>       Project number to scope issue search (default: 173)
  --branch <ref>       Branch to trigger workflow on (default: main)
  --limit <n>          Max issues to process (default: 20)
  --type <type>        Filter by issue type: Bug, Task, Feature, Epic, etc.
  --label <name>       Filter by label (e.g. "severity/high", "component/tasklist")
  --skip-assigned      Skip issues that already have org-level urgency set
  --updated-since <duration|ISO>
                       Only issues updated within the given window. Accepts
                       ISO 8601 timestamp (2026-04-23T05:00:00Z) or a
                       relative duration: 1h, 2d, 1w (hours, days, weeks)
  --delay <seconds>    Delay between workflow dispatches (default: 0)
  --dry-run            Show what would be triggered without running anything
  -h, --help           Show this help

Examples:
  $0                                    # Defaults: project 173, limit 20
  $0 --type Bug --skip-assigned         # Only bugs without urgency
  $0 --project 187 --type Bug --limit 50 --dry-run
  $0 --label "severity/high" --limit 10
  $0 --repo web-modeler --project 187 --skip-assigned
  $0 --project 187 --updated-since 1h --skip-assigned  # For hourly cron
EOF
  exit 0
}

require_arg() {
  if [[ $# -lt 2 || -z "$2" || "$2" == --* ]]; then
    echo "Error: $1 requires a value"; exit 1
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)       require_arg "$@"; REPO_NAME="$2"; shift 2 ;;
    --project)    require_arg "$@"; PROJECT_ID="$2"; shift 2 ;;
    --branch)     require_arg "$@"; BRANCH="$2"; shift 2 ;;
    --limit)      require_arg "$@"; LIMIT="$2"; shift 2 ;;
    --type)       require_arg "$@"; ISSUE_TYPE="$2"; shift 2 ;;
    --label)      require_arg "$@"; LABEL="$2"; shift 2 ;;
    --skip-assigned) SKIP_ASSIGNED=true; shift ;;
    --updated-since) require_arg "$@"; UPDATED_SINCE="$2"; shift 2 ;;
    --delay)      require_arg "$@"; DELAY="$2"; shift 2 ;;
    --dry-run)    DRY_RUN=true; shift ;;
    -h|--help)    usage ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

# Validate numeric arguments
for var_name in LIMIT DELAY PROJECT_ID; do
  eval "val=\$$var_name"
  if ! [[ "$val" =~ ^[0-9]+$ ]]; then
    echo "Error: --${var_name,,} must be a positive integer, got '$val'"
    exit 1
  fi
done

# --- Resolve --updated-since to ISO timestamp ---
if [[ -n "$UPDATED_SINCE" ]]; then
  if [[ "$UPDATED_SINCE" =~ ^[0-9]+[hdw]$ ]]; then
    NUM="${UPDATED_SINCE%[hdw]}"
    UNIT="${UPDATED_SINCE: -1}"
    case "$UNIT" in
      h) SECS=$((NUM * 3600)) ;;
      d) SECS=$((NUM * 86400)) ;;
      w) SECS=$((NUM * 604800)) ;;
    esac
    UPDATED_SINCE=$(date -u -v-${SECS}S +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
      || date -u -d "-${SECS} seconds" +"%Y-%m-%dT%H:%M:%SZ")
  fi
  # Validate ISO format
  if ! [[ "$UPDATED_SINCE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T ]]; then
    echo "Error: --updated-since must be ISO 8601 (2026-04-23T05:00:00Z) or relative (1h, 2d, 1w)"
    exit 1
  fi
fi

# --- Build search query ---
SEARCH_QUERY="repo:${ORG_NAME}/${REPO_NAME} is:issue is:open project:${ORG_NAME}/${PROJECT_ID}"

if [[ -n "$LABEL" ]]; then
  SEARCH_QUERY="$SEARCH_QUERY label:\"$LABEL\""
fi

if [[ -n "$UPDATED_SINCE" ]]; then
  SEARCH_QUERY="$SEARCH_QUERY updated:>$UPDATED_SINCE"
fi

# --- Summary ---
echo "Configuration:"
echo "  Repository:    ${ORG_NAME}/${REPO_NAME}"
echo "  Project:       #$PROJECT_ID"
echo "  Branch:        $BRANCH"
echo "  Limit:         $LIMIT"
[[ -n "$ISSUE_TYPE" ]]    && echo "  Type filter:   $ISSUE_TYPE"
[[ -n "$LABEL" ]]         && echo "  Label filter:  $LABEL"
[[ -n "$UPDATED_SINCE" ]] && echo "  Updated since: $UPDATED_SINCE"
[[ "$SKIP_ASSIGNED" == "true" ]] && echo "  Skip assigned: yes"
[[ "$DRY_RUN" == "true" ]] && echo "  Mode:          DRY RUN"
[[ "$DELAY" -gt 0 ]] && echo "  Delay:         ${DELAY}s between dispatches"
echo ""

# --- Fetch issues with pagination ---
echo "Fetching up to $LIMIT issues from project $PROJECT_ID..."

ISSUE_NUMBERS=""
ISSUE_COUNT=0
CURSOR="null"
PAGE_SIZE=50
MAX_PAGES=20

for ((page=1; page<=MAX_PAGES; page++)); do
  if [[ "$CURSOR" == "null" ]]; then
    CURSOR_ARG=""
  else
    CURSOR_ARG=", after: \"$CURSOR\""
  fi

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
            issueType { name }
            issueFieldValues(first: 50) {
              nodes {
                ... on IssueFieldSingleSelectValue {
                  field { ... on IssueFieldSingleSelect { id } }
                  name
                }
              }
            }
            projectItems(first: 20) {
              nodes {
                project { number }
                fieldValues(first: 20) {
                  nodes {
                    ... on ProjectV2ItemFieldSingleSelectValue {
                      field { ... on ProjectV2SingleSelectField { name } }
                      name
                    }
                  }
                }
              }
            }
          }
        }
      }
    }" -F searchQuery="$SEARCH_QUERY")

  # Apply client-side filters via jq (values passed safely via --arg)
  # --skip-assigned: skip issues that have BOTH org-level urgency AND project-level urgency set
  PAGE_ISSUES=$(echo "$RESPONSE" | jq -r \
    --arg issueType "$ISSUE_TYPE" \
    --arg skipAssigned "$SKIP_ASSIGNED" \
    --arg fieldId "$URGENCY_FIELD_ID" \
    --argjson projectId "$PROJECT_ID" \
    '.data.search.nodes[]
     | if $issueType != "" then select(.issueType.name == $issueType) else . end
     | if $skipAssigned == "true" then select(
         (.issueFieldValues.nodes | map(select(.field.id == $fieldId)) | length == 0)
         or (.projectItems.nodes[] | select(.project.number == $projectId) | .fieldValues.nodes | map(select(.field.name == "Urgency")) | length == 0)
       ) else . end
     | .number')

  if [[ -n "$PAGE_ISSUES" ]]; then
    if [[ -z "$ISSUE_NUMBERS" ]]; then
      ISSUE_NUMBERS="$PAGE_ISSUES"
    else
      ISSUE_NUMBERS="$ISSUE_NUMBERS"$'\n'"$PAGE_ISSUES"
    fi
    ISSUE_COUNT=$(echo "$ISSUE_NUMBERS" | wc -l | tr -d ' ')
  fi

  echo "  Page $page: Found $ISSUE_COUNT issue(s) so far..."

  if [[ $ISSUE_COUNT -ge $LIMIT ]]; then
    echo "  Reached target of $LIMIT issues"
    ISSUE_NUMBERS=$(echo "$ISSUE_NUMBERS" | head -n "$LIMIT")
    break
  fi

  HAS_NEXT=$(echo "$RESPONSE" | jq -r '.data.search.pageInfo.hasNextPage')
  if [[ "$HAS_NEXT" != "true" ]]; then
    echo "  No more pages available"
    break
  fi

  CURSOR=$(echo "$RESPONSE" | jq -r '.data.search.pageInfo.endCursor')
done

if [[ -z "$ISSUE_NUMBERS" ]]; then
  echo "⚠️  No issues found matching the filters."
  exit 1
fi

ISSUE_COUNT=$(echo "$ISSUE_NUMBERS" | wc -l | tr -d ' ')
echo "✅ Found $ISSUE_COUNT issue(s) to process"
echo ""

# --- Dispatch workflows ---
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔍 DRY RUN — would trigger workflow for:"
  for ISSUE_NUMBER in $ISSUE_NUMBERS; do
    echo "  → #$ISSUE_NUMBER"
  done
  echo "ℹ️  Dry run complete. Would have triggered $ISSUE_COUNT workflow(s)."
else
  echo "Triggering 'assign-urgency-to-issue.yml' for $ISSUE_COUNT issue(s):"
  DISPATCHED=0
  for ISSUE_NUMBER in $ISSUE_NUMBERS; do
    echo "→ #$ISSUE_NUMBER"
    gh workflow run assign-urgency-to-issue.yml \
      --ref "$BRANCH" \
      -f issue_number="$ISSUE_NUMBER"
    ((DISPATCHED++))
    if [[ "$DELAY" -gt 0 && $DISPATCHED -lt $ISSUE_COUNT ]]; then
      sleep "$DELAY"
    fi
  done
  echo "✅ Done! Triggered $ISSUE_COUNT workflow(s)."
fi
