#!/bin/bash
# Collects all stale (open) backport PRs from one or more repositories.
# Outputs a JSON array of backport PR objects grouped by original PR,
# with a "repository" field on each group.
#
# Required env vars:
#   GH_TOKEN           - GitHub token with repo read access (needs cross-repo access if multiple repos)
#
# Optional env vars:
#   REPOSITORIES          - Comma-separated list of owner/repo to scan (default: $GITHUB_REPOSITORY)
#   GITHUB_REPOSITORY     - Fallback when REPOSITORIES is not set
#   STALE_HOURS_THRESHOLD - Hours after which a backport PR is considered stale (default: 24)
#   TARGET_BRANCH         - Filter to a specific stable branch (e.g. "stable/8.8"), empty = all

set -euo pipefail

STALE_HOURS_THRESHOLD="${STALE_HOURS_THRESHOLD:-24}"
TARGET_BRANCH="${TARGET_BRANCH:-}"

# Build list of repositories to scan
if [[ -n "${REPOSITORIES:-}" ]]; then
  IFS=',' read -ra REPO_LIST <<< "$REPOSITORIES"
elif [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
  REPO_LIST=("$GITHUB_REPOSITORY")
else
  echo "❌ Either REPOSITORIES or GITHUB_REPOSITORY must be set" >&2
  exit 1
fi

DATE_PROGRAM=date
if command -v gdate >/dev/null; then
  DATE_PROGRAM=gdate
fi

current_timestamp=$("$DATE_PROGRAM" +%s)

# Use temp files for large JSON to avoid shell variable escaping issues
TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

echo "🔍 Collecting open backport PRs from: ${REPO_LIST[*]}" >&2
if [[ -n "$TARGET_BRANCH" ]]; then
  echo "🎯 Filtering to target branch: $TARGET_BRANCH" >&2
fi
echo "⏰ Stale threshold: ${STALE_HOURS_THRESHOLD} hours" >&2

# Collect from all repositories into a single result
echo '[]' > "$TMPDIR_WORK/all_groups.json"
total_pr_count=0

for repo in "${REPO_LIST[@]}"; do
  repo=$(echo "$repo" | xargs) # trim whitespace
  echo "" >&2
  echo "📦 Scanning ${repo}..." >&2

  # Common gh pr list args
  json_fields="number,title,url,body,createdAt,isDraft,baseRefName,author,assignees,labels"
  common_args=(--repo "$repo" --state open --limit 1000 --json "$json_fields")

  # Build branch filter
  branch_args=()
  if [[ -n "$TARGET_BRANCH" ]]; then
    branch_args+=(--base "$TARGET_BRANCH")
  else
    branch_args+=(--search "base:stable/")
  fi

  # Fetch backport PRs created by backport-action targeting stable branches
  gh pr list "${common_args[@]}" "${branch_args[@]}" --author backport-action > "$TMPDIR_WORK/backport_prs.json"

  pr_count=$(jq 'length' "$TMPDIR_WORK/backport_prs.json")
  echo "  📋 Found $pr_count open backport PR(s) on stable/* branches" >&2
  total_pr_count=$((total_pr_count + pr_count))

  if [[ "$pr_count" -eq 0 ]]; then
    continue
  fi

  # Process each backport PR: compute age, extract original PR number, filter by threshold
  jq -c --arg now "$current_timestamp" --arg threshold "$STALE_HOURS_THRESHOLD" --arg repo "$repo" '
    [.[] |
      # Extract original PR number from the body ("Backport of #NNNN")
      (.body // "" | try capture("Backport of #(?<num>[0-9]+)") catch {num: null}) as $orig |

      # Calculate age in hours (createdAt format: "2025-01-15T10:30:00Z")
      ((.createdAt | strptime("%Y-%m-%dT%H:%M:%SZ") | mktime) // 0) as $created |
      ((($now | tonumber) - $created) / 60 | floor) as $age_minutes |
      (($age_minutes / 60) | floor) as $age_hours |

      # Only include PRs older than threshold
      select($age_hours >= ($threshold | tonumber)) |

      {
        backport_pr_number: .number,
        backport_pr_url: .url,
        backport_pr_title: .title,
        target_branch: .baseRefName,
        is_draft: .isDraft,
        created_at: .createdAt,
        age_minutes: $age_minutes,
        age_hours: $age_hours,
        age_days: (($age_hours / 24) | floor),
        original_pr_number: ($orig.num // null | if . then tonumber else null end),
        author: .author.login,
        assignees: [.assignees[].login],
        labels: [.labels[].name],
        ci_status: (if .isDraft then "draft" else "unknown" end),
        repository: $repo
      }
    ] | sort_by(-.age_hours)
  ' "$TMPDIR_WORK/backport_prs.json" > "$TMPDIR_WORK/result.json"

  # Group by original PR number
  jq -c '
    group_by(.original_pr_number) |
    [.[] | {
      original_pr_number: .[0].original_pr_number,
      repository: .[0].repository,
      backport_prs: .
    }]
  ' "$TMPDIR_WORK/result.json" > "$TMPDIR_WORK/grouped.json"

  # Enrich each group with original PR title, URL, and author
  jq -c '.[]' "$TMPDIR_WORK/grouped.json" | while IFS= read -r group; do
    orig_num=$(echo "$group" | jq -r '.original_pr_number // "null"')

    if [[ "$orig_num" != "null" ]]; then
      orig_data=$(gh pr view "$orig_num" \
        --repo "$repo" \
        --json title,url,author,assignees \
        --jq '{title: .title, url: .url, author: .author.login, assignees: [.assignees[].login]}' 2>/dev/null \
        || echo '{"title": "Unknown", "url": "", "author": "unknown", "assignees": []}')

      echo "$group" | jq -c \
        --argjson orig "$orig_data" \
        '. + {original_pr_title: $orig.title, original_pr_url: $orig.url, original_pr_author: $orig.author, original_pr_assignees: $orig.assignees}'
    else
      echo "$group" | jq -c '. + {original_pr_title: "Unknown origin", original_pr_url: "", original_pr_author: "unknown", original_pr_assignees: []}'
    fi
  done | jq -s '.' > "$TMPDIR_WORK/repo_groups.json"

  # Merge into all_groups
  jq -s '.[0] + .[1]' "$TMPDIR_WORK/all_groups.json" "$TMPDIR_WORK/repo_groups.json" > "$TMPDIR_WORK/all_groups_tmp.json"
  mv "$TMPDIR_WORK/all_groups_tmp.json" "$TMPDIR_WORK/all_groups.json"

  repo_stale=$(jq '[.[].backport_prs | length] | add // 0' "$TMPDIR_WORK/repo_groups.json")
  echo "  ✅ ${repo}: $repo_stale stale backport PR(s)" >&2
done

# Resolve GitHub usernames to real names (deduplicated)
echo "" >&2
echo "👤 Resolving author names..." >&2

# Collect all unique usernames (authors + assignees)
jq -r '[.[].original_pr_author, .[].original_pr_assignees[]?, .[].backport_prs[].assignees[]?] | unique | .[] | select(. != "unknown" and . != "")' \
  "$TMPDIR_WORK/all_groups.json" > "$TMPDIR_WORK/usernames.txt"

# Build a username→name mapping JSON object
echo '{}' > "$TMPDIR_WORK/name_map.json"
while IFS= read -r username; do
  # Skip bot accounts
  if [[ "$username" =~ ^(backport-action|app/.*)$ ]]; then
    continue
  fi
  real_name=$(gh api "users/${username}" --jq '.name // ""' 2>/dev/null || echo "")
  if [[ -n "$real_name" ]]; then
    jq -c --arg user "$username" --arg name "$real_name" '. + {($user): $name}' \
      "$TMPDIR_WORK/name_map.json" > "$TMPDIR_WORK/name_map_tmp.json" && mv "$TMPDIR_WORK/name_map_tmp.json" "$TMPDIR_WORK/name_map.json"
    echo "  ✅ ${username} → ${real_name}" >&2
  else
    echo "  ⚠️  ${username} → (no name set)" >&2
  fi
done < "$TMPDIR_WORK/usernames.txt"

# Enrich all groups with author_name field
jq --argjson names "$(cat "$TMPDIR_WORK/name_map.json")" '
  [.[] | . + {
    original_pr_author_name: ($names[.original_pr_author] // null),
    backport_prs: [.backport_prs[] | . + {
      assignee_names: [.assignees[] | {username: ., name: ($names[.] // null)}]
    }]
  }]
' "$TMPDIR_WORK/all_groups.json" > "$TMPDIR_WORK/all_groups_enriched.json"
mv "$TMPDIR_WORK/all_groups_enriched.json" "$TMPDIR_WORK/all_groups.json"

cat "$TMPDIR_WORK/all_groups.json"

total_stale=$(jq '[.[].backport_prs | length] | add // 0' "$TMPDIR_WORK/all_groups.json")
total_groups=$(jq 'length' "$TMPDIR_WORK/all_groups.json")
skipped=$((total_pr_count - total_stale))
echo "" >&2
echo "✅ Result: $total_stale of $total_pr_count backport PR(s) are stale (open >${STALE_HOURS_THRESHOLD}h), grouped under $total_groups original PR(s). $skipped recent PR(s) skipped." >&2
