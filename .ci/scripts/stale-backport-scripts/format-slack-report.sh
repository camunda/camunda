#!/bin/bash
# Formats the stale backport JSON data into Slack Block Kit payload.
# Reads the JSON output from collect-stale-backports.sh on stdin.
# Outputs a Slack Block Kit JSON payload to stdout.
# Groups PRs by repository when data comes from multiple repos.
#
# Optional env vars:
#   REPOSITORIES       - Comma-separated list of repos scanned (shows clean status for repos with 0 stale PRs)
#   GITHUB_REPOSITORY  - owner/repo (for workflow link, defaults to camunda/camunda)
#   GITHUB_RUN_ID      - workflow run ID (for workflow link)
#   TARGET_BRANCH      - if set, shows in the header
#   SLACK_USER_MAP      - JSON mapping keyed by GitHub login or real name to Slack user info. Supports two formats:
#                        Simple:  '{"szpraat": "U09B7CWMX4P"}'
#                        Rich:    '{"szpraat": {"slack_id": "U09B7CWMX4P", "avatar_url": "https://..."}}'
#                        GitHub login keys are preferred (reliable); real-name keys also supported for backward compat.
#   SLACK_USER_MAP_FILE - Alternative to SLACK_USER_MAP: path to a JSON file with the same format.
#                        If both are set, SLACK_USER_MAP_FILE takes precedence.

set -euo pipefail

TARGET_BRANCH="${TARGET_BRANCH:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-camunda/camunda}"
GITHUB_RUN_ID="${GITHUB_RUN_ID:-}"
REPOSITORIES="${REPOSITORIES:-}"

# Default to empty JSON object if no Slack user map provided
if [[ -n "${SLACK_USER_MAP_FILE:-}" && -f "${SLACK_USER_MAP_FILE}" ]]; then
  SLACK_USER_MAP=$(cat "$SLACK_USER_MAP_FILE")
elif [[ -z "${SLACK_USER_MAP:-}" ]]; then
  SLACK_USER_MAP='{}'
fi

# Use temp files to avoid shell variable escaping issues with JSON
TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

# Save Slack user map to temp file for safe jq access
printf '%s\n' "$SLACK_USER_MAP" > "$TMPDIR_WORK/slack_user_map.json"

cat > "$TMPDIR_WORK/stale_data_raw.json"

# Sort groups by the oldest backport PR age (descending — oldest first)
jq 'sort_by(-([.backport_prs[].age_hours] | max))' "$TMPDIR_WORK/stale_data_raw.json" > "$TMPDIR_WORK/stale_data.json"

total_groups=$(jq 'length' "$TMPDIR_WORK/stale_data.json")
total_prs=$(jq '[.[].backport_prs | length] | add // 0' "$TMPDIR_WORK/stale_data.json")

if [[ "$total_prs" -eq 0 ]]; then
  branch_note=""
  if [[ -n "$TARGET_BRANCH" ]]; then
    branch_note=" for \`${TARGET_BRANCH}\`"
  fi

  jq -n --arg note "$branch_note" '{
    blocks: [
      {
        type: "section",
        text: {
          type: "mrkdwn",
          text: ("✅ *No stale backport PRs*" + $note + "\n\nAll backport PRs are merged or up to date.")
        }
      }
    ]
  }'
  exit 0
fi

# Determine unique repositories
repo_count=$(jq '[.[].repository // "unknown"] | unique | length' "$TMPDIR_WORK/stale_data.json")

# Build the Slack message
branch_header=""
if [[ -n "$TARGET_BRANCH" ]]; then
  branch_header=" — \`${TARGET_BRANCH}\`"
fi

workflow_link=""
if [[ -n "$GITHUB_RUN_ID" ]]; then
  workflow_link=" • <https://github.com/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}|View workflow>"
fi

# Build blocks array incrementally using jq and temp file
echo '[]' > "$TMPDIR_WORK/blocks.json"

# Header block with last-updated timestamp
DATE_PROGRAM=date
if command -v gdate >/dev/null; then
  DATE_PROGRAM=gdate
fi
updated_at=$("$DATE_PROGRAM" -u '+%H:%M UTC')

jq -c --arg header "⏰ *Stale Backport PRs*${branch_header}  ·  _Updated ${updated_at}_" \
  '. + [{type: "section", text: {type: "mrkdwn", text: $header}}]' \
  "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"

# Summary line
jq -c --argjson total "$total_prs" --argjson groups "$total_groups" --argjson repos "$repo_count" \
  '. + [{type: "section", text: {type: "mrkdwn", text: ("Found *\($total)* stale backport PR(s) across *\($groups)* original PR(s) in *\($repos)* repo(s)")}}]' \
  "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"

# Build the list of repos to show: union of repos in data + REPOSITORIES env var
jq -n --arg repos "$REPOSITORIES" --argjson data "$(jq -c '[.[].repository // "unknown"] | unique' "$TMPDIR_WORK/stale_data.json")" '
  ($data) as $from_data |
  (if $repos != "" then [$repos | split(",")[] | gsub("^\\s+|\\s+$"; "")] else [] end) as $from_env |
  ($from_data + $from_env) | unique | .[]
' > "$TMPDIR_WORK/repo_list.txt"

# Determine whether to use proper divider blocks between PR groups, or fall back to text separators.
# Block budget: 2 (header+summary) + total_repos*2 (divider+repo_header)
#             + repos_with_no_prs (no stale section) + groups*2 (divider+section) + 3 (footer) <= 50
total_repos_in_list=$(wc -l < "$TMPDIR_WORK/repo_list.txt" | tr -d ' ')
repos_with_no_prs=$((total_repos_in_list - repo_count))
estimated_with_dividers=$((2 + total_repos_in_list * 2 + repos_with_no_prs + total_groups * 2 + 3))
if [[ "$estimated_with_dividers" -le 50 ]]; then
  USE_GROUP_DIVIDERS=true
else
  USE_GROUP_DIVIDERS=false
fi

# Iterate over all repos
while IFS= read -r repo_name; do
  repo_name=$(echo "$repo_name" | jq -r '.')

  # Divider + repo header
  jq -c --arg repo "$repo_name" \
    '. + [{type: "divider"}, {type: "section", text: {type: "mrkdwn", text: ("📦 *\($repo)*")}}]' \
    "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"

  # Count stale PRs for this repo
  repo_pr_count=$(jq --arg repo "$repo_name" '[.[] | select((.repository // "unknown") == $repo) | .backport_prs | length] | add // 0' "$TMPDIR_WORK/stale_data.json")

  if [[ "$repo_pr_count" -eq 0 ]]; then
    jq -c '. + [{type: "section", text: {type: "mrkdwn", text: "✅ No stale backport PRs"}}]' \
      "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
  else
    # Filter groups for this repo and iterate
    jq -c --arg repo "$repo_name" '[.[] | select((.repository // "unknown") == $repo)] | .[]' "$TMPDIR_WORK/stale_data.json" | while IFS= read -r group; do
    # Build section text for this group in jq (safe from shell escaping)
    section_text=$(echo "$group" | jq -r --slurpfile slack_map "$TMPDIR_WORK/slack_user_map.json" '
      # Normalize map: support both simple ("key": "UID") and rich ("key": {"slack_id": "UID", ...}) formats
      ($slack_map[0] | with_entries(
        if (.value | type) == "string" then .value = {slack_id: .value}
        else . end
      )) as $smap |
      # Lookup by GitHub login first, then by real name (backward compat)
      def slack_entry_for(login; name):
        ($smap[login] // null) // (if name then $smap[name] // null else null end);
      def slack_id_for(login; name): (slack_entry_for(login; name) | .slack_id // null);

      # For bot-authored PRs, show bot name + tagged approvers inline
      (if (.original_pr_author // "") | test("^app/") then
        "🤖 \(.original_pr_author)" +
        (if (.original_pr_approver_names // []) | length > 0 then
          ", reviewed by " + ([.original_pr_approver_names[] |
            if slack_id_for(.username; .name) then "<@\(slack_id_for(.username; .name))>"
            elif .name then "@\(.name)"
            else "@\(.username)" end
          ] | join(", "))
        else "" end)
      else "" end) as $bot_info |

      # Header with original PR — author mention inline for non-bot PRs
      "*Original PR:* " +
      (if .original_pr_url != "" then
        ":pull-request-merged: <\(.original_pr_url)|#\(.original_pr_number // "?")> \(.original_pr_title // "Unknown" | if length > 60 then .[:57] + "..." else . end)"
      else
        ":pull-request-merged: #\(.original_pr_number // "?") — \(.original_pr_title // "Unknown" | if length > 60 then .[:57] + "..." else . end)"
      end) + (if $bot_info != "" then "  · \($bot_info)"
      else
        (slack_id_for(.original_pr_author // ""; .original_pr_author_name // null)) as $sid |
        if $sid then "  <@\($sid)>"
        elif ((.original_pr_author_name // "") != "") then "  @\(.original_pr_author_name)"
        elif ((.original_pr_author // "") != "") then "  \(.original_pr_author)"
        else "" end
      end) + "\n" +
      "*Stale backports:*\n" +
      ([.backport_prs[] |
        (if .age_days > 0 then "\(.age_days)d \(.age_hours % 24)h" else "\(.age_hours)h \((.age_minutes // 0) % 60)m" end) as $age |
        (if .is_draft then ":pr-draft:" else ":pull-request-open:" end) as $state_emoji |
        (if .has_conflict then " :warning:" else "" end) as $conflict_indicator |
        "  ↳ \($state_emoji)\($conflict_indicator) <\(.backport_pr_url)|#\(.backport_pr_number)> → `\(.target_branch)` (created *\($age)* ago)"
      ] | join("\n"))
    ')

    # Resolve author avatar for section accessory
    author_login=$(echo "$group" | jq -r '.original_pr_author // "unknown"')
    author_name=$(echo "$group" | jq -r '.original_pr_author_name // ""')
    map_entry=$(jq -c --arg login "$author_login" --arg name "$author_name" \
      '(.[$login] // .[$name] // null) | if type == "string" then {slack_id: .} else . end' \
      "$TMPDIR_WORK/slack_user_map.json" 2>/dev/null || echo 'null')
    avatar_url=$(echo "$map_entry" | jq -r '.avatar_url // .image_48 // empty' 2>/dev/null || true)
    if [[ -z "$avatar_url" && "$author_login" != app/* && "$author_login" != "unknown" ]]; then
      avatar_url="https://github.com/${author_login}.png?size=24"
    fi

    if [[ "$USE_GROUP_DIVIDERS" == "true" ]]; then
      if [[ -n "$avatar_url" ]]; then
        jq -c --arg text "$section_text" --arg avatar "$avatar_url" --arg alt "${author_name:-$author_login}" \
          '. + [{type: "divider"}, {type: "section", text: {type: "mrkdwn", text: $text}, accessory: {type: "image", image_url: $avatar, alt_text: $alt}}]' \
          "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
      else
        jq -c --arg text "$section_text" \
          '. + [{type: "divider"}, {type: "section", text: {type: "mrkdwn", text: $text}}]' \
          "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
      fi
    else
      if [[ -n "$avatar_url" ]]; then
        jq -c --arg text "$section_text" --arg avatar "$avatar_url" --arg alt "${author_name:-$author_login}" \
          '. + [{type: "section", text: {type: "mrkdwn", text: ("── ── ── ── ──\n" + $text)}, accessory: {type: "image", image_url: $avatar, alt_text: $alt}}]' \
          "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
      else
        jq -c --arg text "$section_text" \
          '. + [{type: "section", text: {type: "mrkdwn", text: ("── ── ── ── ──\n" + $text)}}]' \
          "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
      fi
    fi
  done
  fi
done < "$TMPDIR_WORK/repo_list.txt"

# Legend + footer
# Slack enforces a hard limit of 50 blocks per message. Trim excess content blocks first.
# Each PR group uses 1 block (section with optional image accessory), plus dividers if enabled.
# With group dividers: limit=47 (50 - 3 footer).
# Without group dividers: limit=46 (reserve slots for truncation notice + footer).
if [[ "$USE_GROUP_DIVIDERS" == "true" ]]; then
  CONTENT_LIMIT=47
else
  CONTENT_LIMIT=46
fi
content_count=$(jq 'length' "$TMPDIR_WORK/blocks.json")
if [[ "$content_count" -gt "$CONTENT_LIMIT" ]]; then
  jq --argjson limit "$CONTENT_LIMIT" '.[0:$limit]' "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json"
  mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
  jq -c '. + [{type: "section", text: {type: "mrkdwn", text: "⚠️ _Report truncated — too many stale PRs to display in a single Slack message. View the full list in the workflow run._"}}]' \
    "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
fi

jq -c --arg wf "$workflow_link" \
  '. + [
    {type: "divider"},
    {type: "context", elements: [{type: "mrkdwn", text: ":pull-request-merged: Merged original PR  •  :pull-request-open: Open backport  •  :pr-draft: Draft backport  •  :warning: Merge conflict detected"}]},
    {type: "context", elements: [{type: "mrkdwn", text: ("🤖 Stale backport tracker" + $wf)}]}
  ]' "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"

# Output final payload
jq '{blocks: .}' "$TMPDIR_WORK/blocks.json"
