#!/bin/bash
# Resolves GitHub real names to Slack user IDs using the Slack users.list API.
# Reads a JSON array of name strings on stdin (e.g., ["Nikola Koevski", "Euro"]).
# Outputs a JSON mapping to stdout: {"Nikola Koevski": "U123ABC", ...}
#
# Required env vars:
#   SLACK_BOT_TOKEN - Slack bot token with users:read scope
#
# Matching strategy (first unique match wins):
#   1. Exact match on real_name (case-insensitive)
#   2. Exact match on display_name (case-insensitive)
#   3. First-name match: first word of GitHub name matches first word of real_name
#   4. Parenthetical-stripped real_name match (e.g., "John Smith (Contractor)" → "john smith")
#   5. Accent-normalized real_name match (e.g., "Vinícius" → "vinicius")
#   6. Normalized display_name match (underscores→spaces, "-ext" suffix stripped)
#   7. Combined parenthetical stripping + accent normalization

set -euo pipefail

if [[ -z "${SLACK_BOT_TOKEN:-}" ]]; then
  echo "⚠️  SLACK_BOT_TOKEN not set — skipping Slack user resolution" >&2
  echo '{}'
  exit 0
fi

TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

# Read names to resolve from stdin
cat > "$TMPDIR_WORK/names.json"

name_count=$(jq 'length' "$TMPDIR_WORK/names.json")
if [[ "$name_count" -eq 0 ]]; then
  echo '{}'
  exit 0
fi

echo "🔍 Resolving $name_count name(s) to Slack user IDs..." >&2

# Fetch all workspace members (paginated)
echo '[]' > "$TMPDIR_WORK/all_members.json"
cursor=""

while true; do
  url="https://slack.com/api/users.list?limit=200"
  if [[ -n "$cursor" ]]; then
    url="${url}&cursor=${cursor}"
  fi

  curl -sf -H "Authorization: Bearer ${SLACK_BOT_TOKEN}" "$url" > "$TMPDIR_WORK/page.json" || {
    echo "❌ Slack API request failed" >&2
    echo '{}'
    exit 0
  }

  # Check for API errors
  ok=$(jq -r '.ok' "$TMPDIR_WORK/page.json")
  if [[ "$ok" != "true" ]]; then
    error=$(jq -r '.error // "unknown"' "$TMPDIR_WORK/page.json")
    echo "❌ Slack API error: $error" >&2
    echo '{}'
    exit 0  # Don't fail the pipeline, just return empty mapping
  fi

  # Extract non-deleted, non-bot members with their names
  jq -c '[.members[] | select(.deleted == false and .is_bot == false and .id != "USLACKBOT") | {
    id: .id,
    real_name_lower: ((.real_name // "") | ascii_downcase),
    display_name_lower: ((.profile.display_name // "") | ascii_downcase),
    display_name_normalized: ((.profile.display_name // "") | ascii_downcase | gsub("_"; " ") | gsub("\\s*-\\s*ext$"; "") | gsub("^\\s+|\\s+$"; ""))
  }]' "$TMPDIR_WORK/page.json" > "$TMPDIR_WORK/page_members.json"

  # Append to all members
  jq -s '.[0] + .[1]' "$TMPDIR_WORK/all_members.json" "$TMPDIR_WORK/page_members.json" \
    > "$TMPDIR_WORK/all_members_tmp.json" && mv "$TMPDIR_WORK/all_members_tmp.json" "$TMPDIR_WORK/all_members.json"

  # Check for next page
  cursor=$(jq -r '.response_metadata.next_cursor // ""' "$TMPDIR_WORK/page.json")
  if [[ -z "$cursor" ]]; then
    break
  fi
done

member_count=$(jq 'length' "$TMPDIR_WORK/all_members.json")
echo "  📋 Fetched $member_count workspace members" >&2

# Resolve each name against the member list
echo '{}' > "$TMPDIR_WORK/result_map.json"

# Helper: normalize accented characters (iconv transliteration)
normalize_accents() {
  local result
  if command -v iconv >/dev/null 2>&1; then
    result=$(echo "$1" | iconv -f UTF-8 -t ASCII//TRANSLIT 2>/dev/null | sed "s/'//g") || true
    if [[ -n "$result" ]]; then
      echo "$result"
      return
    fi
  fi
  echo "$1"
}

while IFS= read -r name; do
  name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]')
  # Strip parenthetical suffixes: "Christopher Kujawa (Zell)" → "christopher kujawa"
  name_stripped=$(echo "$name_lower" | sed 's/ *([^)]*)//g' | sed 's/ *$//;s/^ *//')
  # Accent-normalized version: "vinícius" → "vinicius"
  name_normalized=$(normalize_accents "$name_lower")

  # Strategy 1: Exact match on real_name (case-insensitive), unique only
  slack_id=$(jq -r --arg name "$name_lower" \
    '[.[] | select(.real_name_lower == $name)] | if length == 1 then .[0].id else "" end' \
    "$TMPDIR_WORK/all_members.json")

  # Strategy 2: Exact match on display_name (case-insensitive), unique only
  if [[ -z "$slack_id" ]]; then
    slack_id=$(jq -r --arg name "$name_lower" \
      '[.[] | select(.display_name_lower == $name)] | if length == 1 then .[0].id else "" end' \
      "$TMPDIR_WORK/all_members.json")
  fi

  # Strategy 3: First name of GitHub name matches first word of real_name, unique only
  if [[ -z "$slack_id" ]]; then
    name_first=$(echo "$name_lower" | awk '{print $1}')
    slack_id=$(jq -r --arg name "$name_first" \
      '[.[] | select((.real_name_lower | split(" ") | first) == $name)] | if length == 1 then .[0].id else "" end' \
      "$TMPDIR_WORK/all_members.json")
  fi

  # Strategy 4: Strip parenthetical suffix and retry real_name match
  if [[ -z "$slack_id" && "$name_stripped" != "$name_lower" ]]; then
    slack_id=$(jq -r --arg name "$name_stripped" \
      '[.[] | select(.real_name_lower == $name)] | if length == 1 then .[0].id else "" end' \
      "$TMPDIR_WORK/all_members.json")
  fi

  # Strategy 5: Accent-normalized match on real_name (e.g., Vinícius → Vinicius)
  if [[ -z "$slack_id" && "$name_normalized" != "$name_lower" ]]; then
    slack_id=$(jq -r --arg name "$name_normalized" \
      '[.[] | select(.real_name_lower == $name)] | if length == 1 then .[0].id else "" end' \
      "$TMPDIR_WORK/all_members.json")
  fi

  # Strategy 6: Match on display_name normalized (underscores→spaces, -ext stripped)
  if [[ -z "$slack_id" ]]; then
    slack_id=$(jq -r --arg name "$name_lower" \
      '[.[] | select(.display_name_normalized == $name)] | if length == 1 then .[0].id else "" end' \
      "$TMPDIR_WORK/all_members.json")
  fi

  # Strategy 7: Combine stripping + accent normalization
  if [[ -z "$slack_id" ]]; then
    name_both=$(normalize_accents "$name_stripped")
    if [[ "$name_both" != "$name_lower" ]]; then
      slack_id=$(jq -r --arg name "$name_both" \
        '[.[] | select(.real_name_lower == $name)] | if length == 1 then .[0].id else "" end' \
        "$TMPDIR_WORK/all_members.json")
    fi
  fi

  if [[ -n "$slack_id" ]]; then
    jq -c --arg name "$name" --arg id "$slack_id" '. + {($name): $id}' \
      "$TMPDIR_WORK/result_map.json" > "$TMPDIR_WORK/result_map_tmp.json" \
      && mv "$TMPDIR_WORK/result_map_tmp.json" "$TMPDIR_WORK/result_map.json"
    echo "  ✅ ${name} → ${slack_id}" >&2
  else
    echo "  ⚠️  ${name} → (no Slack match)" >&2
  fi
done < <(jq -r '.[]' "$TMPDIR_WORK/names.json")

resolved=$(jq 'length' "$TMPDIR_WORK/result_map.json")
echo "  📊 Resolved $resolved of $name_count name(s)" >&2

cat "$TMPDIR_WORK/result_map.json"
