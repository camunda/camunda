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
#   SLACK_USER_MAP     - JSON mapping of real names to Slack user IDs (e.g. '{"Peter Szabo": "U09B7CWMX4P"}')

set -euo pipefail

TARGET_BRANCH="${TARGET_BRANCH:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-camunda/camunda}"
GITHUB_RUN_ID="${GITHUB_RUN_ID:-}"
REPOSITORIES="${REPOSITORIES:-}"

# Default to empty JSON object if no Slack user map provided
if [[ -z "${SLACK_USER_MAP:-}" ]]; then
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
      $slack_map[0] as $smap |
      # Author display: for bot-authored PRs show bot name + approvers; otherwise <@USLACKID>, @RealName, or GitHub username
      (
        if (.original_pr_author // "") | test("^app/") then
          # Bot-authored PR: show bot name and tag approvers
          ("🤖 \(.original_pr_author)" +
          (if (.original_pr_approver_names // []) | length > 0 then
            ", reviewed by " + ([.original_pr_approver_names[] |
              if .name and ($smap[.name] // null) then "<@\($smap[.name])>"
              elif .name then "@\(.name)"
              else "@\(.username)" end
            ] | join(", "))
          else
            ""
          end))
        elif .original_pr_author_name and $smap[.original_pr_author_name] then
          "<@\($smap[.original_pr_author_name])>"
        elif .original_pr_author_name then
          "@\(.original_pr_author_name)"
        else
          .original_pr_author // "unknown"
        end
      ) as $author_display |

      # Header with original PR
      "*Original PR:* " +
      (if .original_pr_url != "" then
        ":pull-request-merged: <\(.original_pr_url)|#\(.original_pr_number // "?")> \(.original_pr_title // "Unknown" | if length > 60 then .[:57] + "..." else . end)"
      else
        ":pull-request-merged: #\(.original_pr_number // "?") — \(.original_pr_title // "Unknown" | if length > 60 then .[:57] + "..." else . end)"
      end) + "  · 👤 \($author_display)\n" +
      "*Stale backports:*\n" +
      ([.backport_prs[] |
        (if .age_days > 0 then "\(.age_days)d \(.age_hours % 24)h" else "\(.age_hours)h \((.age_minutes // 0) % 60)m" end) as $age |
        (if .is_draft then ":pr-draft:" else ":pull-request-open:" end) as $state_emoji |
        (if .has_conflict then " :warning:" else "" end) as $conflict_indicator |
        "  ↳ \($state_emoji)\($conflict_indicator) <\(.backport_pr_url)|#\(.backport_pr_number)> → `\(.target_branch)` (created *\($age)* ago)"
      ] | join("\n"))
    ')

    if [[ "$USE_GROUP_DIVIDERS" == "true" ]]; then
      jq -c --arg text "$section_text" \
        '. + [{type: "divider"}, {type: "section", text: {type: "mrkdwn", text: $text}}]' \
        "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
    else
      jq -c --arg text "$section_text" \
        '. + [{type: "section", text: {type: "mrkdwn", text: ("── ── ── ── ──\n" + $text)}}]' \
        "$TMPDIR_WORK/blocks.json" > "$TMPDIR_WORK/blocks_tmp.json" && mv "$TMPDIR_WORK/blocks_tmp.json" "$TMPDIR_WORK/blocks.json"
    fi
  done
  fi
done < "$TMPDIR_WORK/repo_list.txt"

# Legend + footer
# Slack enforces a hard limit of 50 blocks per message. Trim excess content blocks first.
# With group dividers: pre-verified to fit in 50, no truncation notice slot needed (limit=47).
# Without group dividers: reserve 1 slot for the truncation notice section (limit=46).
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
