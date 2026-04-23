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
DIRECT=false
UPDATED_SINCE=""

URGENCY_FIELD_ID="IFSS_kgDNKAw"
# Org-level urgency field option IDs (populated as simple vars to avoid set -u issues with associative arrays)
URGENCY_OPT_immediate="IFSSO_kgDNOUI"
URGENCY_OPT_next="IFSSO_kgDNOUM"
URGENCY_OPT_planned="IFSSO_kgDNOUQ"
URGENCY_OPT_someday="IFSSO_kgDNOUU"

get_urgency_option_id() {
  local var="URGENCY_OPT_$1"
  echo "${!var:-}"
}

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
  --direct             Calculate and set urgency directly via API (no workflow dispatch).
                       Supports cross-repo issues. Requires GH_TOKEN with project write access.
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
  $0 --project 187 --direct --updated-since 1h --skip-assigned  # For hourly cron
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
    --direct)     DIRECT=true; shift ;;
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

# --- Validate flag combinations ---
if [[ "$REPO_NAME" != "camunda" && "$DIRECT" != "true" ]]; then
  echo "Error: --repo requires --direct mode (workflow dispatch only supports camunda/camunda)"
  exit 1
fi

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
  if ! [[ "$UPDATED_SINCE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2} ]]; then
    echo "Error: --updated-since must be ISO 8601 (2026-04-23T05:00:00Z) or relative (1h, 2d, 1w)"
    exit 1
  fi
fi

# --- Build search query ---
if [[ "$DIRECT" == "true" ]]; then
  # Direct mode searches across all repos in the project
  SEARCH_QUERY="org:${ORG_NAME} is:issue is:open project:${ORG_NAME}/${PROJECT_ID}"
else
  SEARCH_QUERY="repo:${ORG_NAME}/${REPO_NAME} is:issue is:open project:${ORG_NAME}/${PROJECT_ID}"
fi

if [[ -n "$LABEL" ]]; then
  SEARCH_QUERY="$SEARCH_QUERY label:\"$LABEL\""
fi

if [[ -n "$UPDATED_SINCE" ]]; then
  SEARCH_QUERY="$SEARCH_QUERY updated:>$UPDATED_SINCE"
fi

# --- Summary ---
echo "Configuration:"
[[ "$DIRECT" == "true" ]] && echo "  Mode:          DIRECT (API)" || echo "  Repository:    ${ORG_NAME}/${REPO_NAME}"
echo "  Project:       #$PROJECT_ID"
[[ "$DIRECT" != "true" ]] && echo "  Branch:        $BRANCH"
echo "  Limit:         $LIMIT"
[[ -n "$ISSUE_TYPE" ]]    && echo "  Type filter:   $ISSUE_TYPE"
[[ -n "$LABEL" ]]         && echo "  Label filter:  $LABEL"
[[ -n "$UPDATED_SINCE" ]] && echo "  Updated since: $UPDATED_SINCE"
[[ "$SKIP_ASSIGNED" == "true" ]] && echo "  Skip assigned: yes"
[[ "$DRY_RUN" == "true" ]] && echo "  Dry run:       yes"
[[ "$DELAY" -gt 0 ]] && echo "  Delay:         ${DELAY}s"
echo ""

# --- Fetch issues with pagination ---
echo "Fetching up to $LIMIT issues from project $PROJECT_ID..."

ISSUE_NUMBERS=""
ISSUE_COUNT=0
CURSOR="null"
PAGE_SIZE=50
MAX_PAGES=20
ALL_ISSUES_JSON=""
# Project field cache file (one lookup per project)
PROJECT_FIELD_CACHE_DIR=$(mktemp -d)
trap 'rm -rf "$PROJECT_FIELD_CACHE_DIR"' EXIT

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
            id
            number
            repository { nameWithOwner }
            issueType { name }
            labels(first:100) { nodes { name } }
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
                id
                project { id number }
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
  PAGE_ISSUES=$(echo "$RESPONSE" | jq -r \
    --arg issueType "$ISSUE_TYPE" \
    --arg skipAssigned "$SKIP_ASSIGNED" \
    --arg fieldId "$URGENCY_FIELD_ID" \
    '.data.search.nodes[]
     | if $issueType != "" then select(.issueType.name == $issueType) else . end
     | if $skipAssigned == "true" then select(.issueFieldValues.nodes | map(select(.field.id == $fieldId)) | length == 0) else . end
     | .number')

  # In direct mode, also store full issue data for later processing
  if [[ "$DIRECT" == "true" ]]; then
    PAGE_JSON=$(echo "$RESPONSE" | jq -c \
      --arg issueType "$ISSUE_TYPE" \
      --arg skipAssigned "$SKIP_ASSIGNED" \
      --arg fieldId "$URGENCY_FIELD_ID" \
      '[.data.search.nodes[]
       | if $issueType != "" then select(.issueType.name == $issueType) else . end
       | if $skipAssigned == "true" then select(.issueFieldValues.nodes | map(select(.field.id == $fieldId)) | length == 0) else . end]')
    if [[ -z "$ALL_ISSUES_JSON" ]]; then
      ALL_ISSUES_JSON="$PAGE_JSON"
    else
      ALL_ISSUES_JSON=$(echo "$ALL_ISSUES_JSON $PAGE_JSON" | jq -sc 'add')
    fi
  fi

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
  if [[ -n "$UPDATED_SINCE" ]]; then
    echo "ℹ️  No issues found matching the filters (--updated-since=$UPDATED_SINCE)."
    exit 0
  fi
  echo "⚠️  No issues found matching the filters."
  exit 1
fi

ISSUE_COUNT=$(echo "$ISSUE_NUMBERS" | wc -l | tr -d ' ')
echo "✅ Found $ISSUE_COUNT issue(s) to process"
echo ""

# --- Urgency calculation (shared by direct mode) ---
calculate_urgency() {
  local labels="$1"
  local severity likelihood impact when urgency_sev urgency_imp

  severity=$(echo "$labels" | jq -r '.[] | select(startswith("severity/")) | sub("severity/"; "") | select(. == "low" or . == "mid" or . == "high" or . == "critical")' | head -n1)
  likelihood=$(echo "$labels" | jq -r '.[] | select(startswith("likelihood/")) | sub("likelihood/"; "") | select(. == "low" or . == "mid" or . == "high" or . == "unknown")' | head -n1)
  impact=$(echo "$labels" | jq -r '.[] | select(startswith("impact/")) | sub("impact/"; "") | select(. == "low" or . == "medium" or . == "high")' | head -n1)
  when=$(echo "$labels" | jq -r '.[] | select(startswith("when/")) | sub("when/"; "") | select(. == "later" or . == "soon" or . == "now")' | head -n1)

  [[ -z "$likelihood" || "$likelihood" == "unknown" ]] && likelihood="low"
  [[ -z "$when" ]] && when="later"

  urgency_sev=""
  if [[ -n "$severity" ]]; then
    case "${severity}_${likelihood}" in
      low_*|mid_low) urgency_sev="someday" ;;
      mid_mid|high_low) urgency_sev="planned" ;;
      mid_high|high_mid|high_high) urgency_sev="next" ;;
      critical_*) urgency_sev="immediate" ;;
      *) urgency_sev="someday" ;;
    esac
  fi

  urgency_imp=""
  if [[ -n "$impact" ]]; then
    case "${impact}_${when}" in
      low_later) urgency_imp="someday" ;;
      low_soon|low_now|medium_later|high_later) urgency_imp="planned" ;;
      medium_soon|medium_now|high_soon) urgency_imp="next" ;;
      high_now) urgency_imp="immediate" ;;
      *) urgency_imp="someday" ;;
    esac
  fi

  # Pick the higher urgency
  urgency_prio() {
    case "$1" in
      immediate) echo 4 ;; next) echo 3 ;; planned) echo 2 ;; someday) echo 1 ;; *) echo 0 ;;
    esac
  }
  if [[ -n "$urgency_sev" && -n "$urgency_imp" ]]; then
    if (( $(urgency_prio "$urgency_imp") >= $(urgency_prio "$urgency_sev") )); then
      echo "$urgency_imp"
    else
      echo "$urgency_sev"
    fi
  elif [[ -n "$urgency_imp" ]]; then
    echo "$urgency_imp"
  elif [[ -n "$urgency_sev" ]]; then
    echo "$urgency_sev"
  fi
}

# --- Process issues ---
if [[ "$DIRECT" == "true" ]]; then
  # Direct mode: calculate urgency and call API directly
  echo "Processing $ISSUE_COUNT issue(s) directly via API..."
  COUNTER_FILE=$(mktemp)
  echo "0 0 0" > "$COUNTER_FILE"

  # Trim ALL_ISSUES_JSON to limit
  ALL_ISSUES_JSON=$(echo "$ALL_ISSUES_JSON" | jq -c ".[:$LIMIT]")

  echo "$ALL_ISSUES_JSON" | jq -c '.[]' | while IFS= read -r ROW; do
    ISSUE_ID=$(echo "$ROW" | jq -r '.id')
    ISSUE_NUM=$(echo "$ROW" | jq -r '.number')
    ISSUE_REPO=$(echo "$ROW" | jq -r '.repository.nameWithOwner')
    LABELS=$(echo "$ROW" | jq -c '.labels.nodes | map(.name)')
    HAS_LOCKED=$(echo "$LABELS" | jq -r 'any(. == "urgency-locked")')

    NEW_URGENCY=$(calculate_urgency "$LABELS")
    read P U S < "$COUNTER_FILE"; echo "$((P+1)) $U $S" > "$COUNTER_FILE"

    if [[ -z "$NEW_URGENCY" ]]; then
      echo "  ⏭️ #$ISSUE_NUM ($ISSUE_REPO): no severity/impact labels"
      read P U S < "$COUNTER_FILE"; echo "$P $U $((S+1))" > "$COUNTER_FILE"
      continue
    fi

    # Check current org-level urgency
    CURRENT=$(echo "$ROW" | jq -r --arg fid "$URGENCY_FIELD_ID" \
      '.issueFieldValues.nodes[] | select(.field.id==$fid) | .name // empty')

    if [[ "$HAS_LOCKED" == "true" ]]; then
      # urgency-locked: skip org-level update but still sync existing value to project
      SYNC_URGENCY="${CURRENT:-$NEW_URGENCY}"
      if [[ -n "$SYNC_URGENCY" ]]; then
        echo "  🔒 #$ISSUE_NUM ($ISSUE_REPO): urgency-locked (syncing '$SYNC_URGENCY' to project)"
        NEW_URGENCY="$SYNC_URGENCY"
      else
        echo "  🔒 #$ISSUE_NUM ($ISSUE_REPO): urgency-locked, no value to sync"
        read P U S < "$COUNTER_FILE"; echo "$P $U $((S+1))" > "$COUNTER_FILE"
        continue
      fi
    else
      LABEL_INFO="→ $NEW_URGENCY"
      [[ -n "$CURRENT" ]] && LABEL_INFO="$CURRENT → $NEW_URGENCY"

      if [[ "$DRY_RUN" == "true" ]]; then
        echo "  🔬 #$ISSUE_NUM ($ISSUE_REPO): $LABEL_INFO (dry run)"
        continue
      fi

      # Update org-level urgency if changed
      if [[ "$CURRENT" != "$NEW_URGENCY" ]]; then
      OPTION_ID=$(get_urgency_option_id "$NEW_URGENCY")
      gh api graphql --silent -H "Content-Type: application/json" --input - <<< "$(jq -n \
        --arg issueId "$ISSUE_ID" \
        --arg fieldId "$URGENCY_FIELD_ID" \
        --arg optionId "$OPTION_ID" \
        '{
          "query": "mutation($input: SetIssueFieldValueInput!) { setIssueFieldValue(input: $input) { issue { id } } }",
          "variables": { "input": { "issueId": $issueId, "issueFields": [{ "fieldId": $fieldId, "singleSelectOptionId": $optionId }] } }
        }')" 2>/dev/null || {
        echo "  ⚠️ #$ISSUE_NUM ($ISSUE_REPO): org-level update failed"
      }
    fi
    fi # end of non-locked branch

    # Sync to project-level urgency field if issue is in project
    PROJECT_ITEM=$(echo "$ROW" | jq -c --argjson pn "$PROJECT_ID" \
      '.projectItems.nodes[] | select(.project.number==$pn)')

    if [[ -n "$PROJECT_ITEM" && "$PROJECT_ITEM" != "null" ]]; then
      P_ITEM_ID=$(echo "$PROJECT_ITEM" | jq -r '.id')
      P_V2_ID=$(echo "$PROJECT_ITEM" | jq -r '.project.id')
      P_CURRENT=$(echo "$PROJECT_ITEM" | jq -r '.fieldValues.nodes[] | select(.field.name=="Urgency") | .name // empty')

      if [[ "$P_CURRENT" != "$NEW_URGENCY" ]]; then
        # Fetch field/option IDs for this project (cached per project via temp files)
        CACHE_FILE="$PROJECT_FIELD_CACHE_DIR/$(echo "$P_V2_ID" | tr -d '/')"
        if [[ ! -f "$CACHE_FILE" ]]; then
          gh api graphql -f query="
            query(\$pid:ID!){ node(id:\$pid){ ... on ProjectV2 { fields(first:100){ nodes { ... on ProjectV2SingleSelectField { id name options { id name } } } } } } }
          " -F pid="$P_V2_ID" > "$CACHE_FILE" 2>/dev/null || echo "{}" > "$CACHE_FILE"
        fi
        P_FIELD_ID=$(jq -r '.data.node.fields.nodes[] | select(.name=="Urgency") | .id // empty' < "$CACHE_FILE")
        P_OPTION_ID=$(jq -r --arg v "$NEW_URGENCY" '.data.node.fields.nodes[] | select(.name=="Urgency") | .options[]? | select(.name==$v) | .id // empty' < "$CACHE_FILE")

        if [[ -n "$P_FIELD_ID" && -n "$P_OPTION_ID" ]]; then
          gh api graphql --silent -H "Content-Type: application/json" --input - <<< "$(jq -n \
            --arg projectId "$P_V2_ID" --arg itemId "$P_ITEM_ID" --arg fieldId "$P_FIELD_ID" --arg optionId "$P_OPTION_ID" \
            '{
              "query": "mutation($input: UpdateProjectV2ItemFieldValueInput!) { updateProjectV2ItemFieldValue(input: $input) { projectV2Item { id } } }",
              "variables": { "input": { "projectId": $projectId, "itemId": $itemId, "fieldId": $fieldId, "value": { "singleSelectOptionId": $optionId } } }
            }')" 2>/dev/null || {
            echo "  ⚠️ #$ISSUE_NUM ($ISSUE_REPO): project sync failed"
          }
        fi
      fi
    fi

    echo "  ✅ #$ISSUE_NUM ($ISSUE_REPO): ${LABEL_INFO:-→ $NEW_URGENCY}"
    read P U S < "$COUNTER_FILE"; echo "$P $((U+1)) $S" > "$COUNTER_FILE"

    if [[ "$DELAY" -gt 0 ]]; then
      sleep "$DELAY"
    fi
  done

  read PROCESSED UPDATED SKIPPED < "$COUNTER_FILE"
  rm -f "$COUNTER_FILE"
  echo ""
  echo "✅ Done! Processed: $PROCESSED, Updated: $UPDATED, Skipped: $SKIPPED"

elif [[ "$DRY_RUN" == "true" ]]; then
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
