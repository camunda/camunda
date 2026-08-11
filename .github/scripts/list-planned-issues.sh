#!/usr/bin/env bash
set -euo pipefail

# List the issues sitting in one column of a GitHub ProjectV2 board as a
# markdown list, one `- [title](url)` line per issue.
#
# Board columns are single-select field options (e.g. an option of "Status" or
# of "Urgency"), so the column name is resolved against the project's
# single-select fields unless --field pins one explicitly.
#
# Requires: gh (authenticated with the read:project scope), jq

# --- Defaults: the camunda planning board, https://github.com/orgs/camunda/projects/249 ---
ORG="camunda"
PROJECT="249"
COLUMN="Planned"
FIELD=""
STATE="open"

usage() {
  cat <<EOF
Usage: $0 [OPTIONS]

List the issues in one column of an org ProjectV2 board as a markdown list.

Options:
  --org <login>       Organization owning the project (default: $ORG)
  --project <number>  Project number (default: $PROJECT)
  --column <name>     Column / single-select option to list (default: $COLUMN)
  --field <name>      Single-select field holding the column, e.g. Status or
                      Urgency (default: auto-detected from the column name)
  --state <state>     Issue state: open, closed or all (default: $STATE)
  -h, --help          Show this help

Examples:
  $0                                     # open issues in "Planned" on project 249
  $0 --column Next --state all           # every issue in "Next", open or closed
  $0 --project 173 --field Status --column "In Progress"
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --org) ORG="$2"; shift 2 ;;
    --project) PROJECT="$2"; shift 2 ;;
    --column) COLUMN="$2"; shift 2 ;;
    --field) FIELD="$2"; shift 2 ;;
    --state) STATE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

case "$STATE" in
  open|closed|all) ;;
  *) echo "Invalid --state '$STATE' (expected open, closed or all)" >&2; exit 1 ;;
esac

for cmd in gh jq; do
  command -v "$cmd" >/dev/null || { echo "Missing required command: $cmd" >&2; exit 1; }
done

# --- Resolve which single-select field owns the requested column ---
FIELDS_JSON=$(gh api graphql \
  -f org="$ORG" -F number="$PROJECT" \
  -f query='
    query($org: String!, $number: Int!) {
      organization(login: $org) {
        projectV2(number: $number) {
          title
          fields(first: 50) {
            nodes {
              ... on ProjectV2SingleSelectField {
                name
                options { name }
              }
            }
          }
        }
      }
    }')

if [[ "$(jq -r '.data.organization.projectV2 // "null"' <<<"$FIELDS_JSON")" == "null" ]]; then
  echo "Project $ORG/$PROJECT not found, or the token lacks the read:project scope." >&2
  exit 1
fi

echo "Board: $(jq -r '.data.organization.projectV2.title' <<<"$FIELDS_JSON") ($ORG/$PROJECT)" >&2

if [[ -z "$FIELD" ]]; then
  FIELD=$(jq -r --arg col "$COLUMN" '
    [ .data.organization.projectV2.fields.nodes[]
      | select(.options != null)
      | select([.options[].name | ascii_downcase] | index($col | ascii_downcase))
      | .name ][0] // empty' <<<"$FIELDS_JSON")
fi

if [[ -z "$FIELD" ]] || ! jq -e --arg f "$FIELD" --arg col "$COLUMN" '
  .data.organization.projectV2.fields.nodes[]
  | select(.options != null and (.name | ascii_downcase) == ($f | ascii_downcase))
  | [.options[].name | ascii_downcase] | index($col | ascii_downcase)' <<<"$FIELDS_JSON" >/dev/null; then
  echo "No column '$COLUMN'${FIELD:+ on field '$FIELD'} in this board. Available columns:" >&2
  jq -r '.data.organization.projectV2.fields.nodes[]
    | select(.options != null)
    | "  \(.name): \([.options[].name] | join(", "))"' <<<"$FIELDS_JSON" >&2
  exit 1
fi

echo "Column: $FIELD = $COLUMN (state: $STATE)" >&2

# --- Page through the board's items ---
ITEMS_FILE=$(mktemp)
trap 'rm -f "$ITEMS_FILE"' EXIT

CURSOR=""
while :; do
  PAGE=$(gh api graphql \
    -f org="$ORG" -F number="$PROJECT" -f field="$FIELD" ${CURSOR:+-f cursor="$CURSOR"} \
    -f query='
      query($org: String!, $number: Int!, $field: String!, $cursor: String) {
        organization(login: $org) {
          projectV2(number: $number) {
            items(first: 100, after: $cursor) {
              pageInfo { hasNextPage endCursor }
              nodes {
                isArchived
                column: fieldValueByName(name: $field) {
                  ... on ProjectV2ItemFieldSingleSelectValue { name }
                }
                content {
                  __typename
                  ... on Issue {
                    title
                    url
                    state
                    updatedAt
                  }
                }
              }
            }
          }
        }
      }')

  jq -c '.data.organization.projectV2.items.nodes[]' <<<"$PAGE" >>"$ITEMS_FILE"

  [[ "$(jq -r '.data.organization.projectV2.items.pageInfo.hasNextPage' <<<"$PAGE")" == "true" ]] || break
  CURSOR=$(jq -r '.data.organization.projectV2.items.pageInfo.endCursor' <<<"$PAGE")
done

# --- Emit the markdown list, newest-updated first (matching the board's sort) ---
# `[` and `]` are escaped so titles like "[8.7] ..." stay valid link text.
jq -rs --arg col "$COLUMN" --arg state "$STATE" '
  map(select(.isArchived == false))
  | map(select(.content.__typename == "Issue"))
  | map(select((.column.name // "" | ascii_downcase) == ($col | ascii_downcase)))
  | map(select($state == "all" or (.content.state | ascii_downcase) == $state))
  | sort_by(.content.updatedAt) | reverse
  | .[]
  | "- [\(.content.title | gsub("\\["; "\\[") | gsub("\\]"; "\\]"))](\(.content.url))"
' "$ITEMS_FILE"
