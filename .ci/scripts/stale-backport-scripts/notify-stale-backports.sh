#!/bin/bash
# Notifies PR authors about stale backport PRs via GitHub comments.
# Reads the JSON output from collect-stale-backports.sh on stdin.
# Each group in the JSON contains a "repository" field used for targeting the correct repo.
#
# For each stale backport PR:
#   - Comments on the backport PR mentioning the assignee(s)
#   - Adds a "stale-backport" label
#
# Required env vars:
#   GH_TOKEN           - GitHub token with PR write access (needs cross-repo access if multiple repos)
#
# Optional env vars:
#   DRY_RUN            - "true" to skip actual GitHub writes (default: "false")
#   NOTIFY_ORIGINAL    - "true" to also comment on the original PR (default: "false")
#   WORKFLOW_RUN_URL   - Full URL of the workflow run that triggered this notification (optional)

set -euo pipefail

DRY_RUN="${DRY_RUN:-false}"
WORKFLOW_RUN_URL="${WORKFLOW_RUN_URL:-}"
NOTIFY_ORIGINAL="${NOTIFY_ORIGINAL:-false}"
RENOTIFY_AFTER_DAYS="${RENOTIFY_AFTER_DAYS:-2}"
STALE_LABEL="stale-backport"
COMMENT_MARKER="<!-- stale-backport-bot -->"

# Use temp files to avoid shell variable escaping issues with JSON
TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

cat > "$TMPDIR_WORK/stale_data.json"

total_groups=$(jq 'length' "$TMPDIR_WORK/stale_data.json")
if [[ "$total_groups" -eq 0 ]]; then
  echo "✅ No stale backport PRs to notify about"
  exit 0
fi

echo "📬 Processing notifications for $total_groups original PR group(s)..." >&2
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔍 DRY-RUN MODE: No comments or labels will be created" >&2
fi

notified_count=0
skipped_count=0
updated_count=0

DATE_PROGRAM=date
if command -v gdate >/dev/null; then
  DATE_PROGRAM=gdate
fi
current_timestamp=$("$DATE_PROGRAM" +%s)
renotify_threshold_seconds=$((RENOTIFY_AFTER_DAYS * 86400))

# Extract scalar fields from each group using jq to avoid shell escaping issues
while IFS= read -r entry; do
  group_idx=$(echo "$entry" | jq -r '.index')

  repo=$(jq -r ".[$group_idx].repository // \"unknown\"" "$TMPDIR_WORK/stale_data.json")
  orig_num=$(jq -r ".[$group_idx].original_pr_number // \"null\"" "$TMPDIR_WORK/stale_data.json")
  orig_title=$(jq -r ".[$group_idx].original_pr_title // \"Unknown\"" "$TMPDIR_WORK/stale_data.json")
  orig_author=$(jq -r ".[$group_idx].original_pr_author // \"unknown\"" "$TMPDIR_WORK/stale_data.json")

  echo "" >&2
  echo "📦 Repository: ${repo}" >&2

  bp_count=$(jq ".[$group_idx].backport_prs | length" "$TMPDIR_WORK/stale_data.json")

  for (( bp_idx=0; bp_idx<bp_count; bp_idx++ )); do
    bp_num=$(jq -r ".[$group_idx].backport_prs[$bp_idx].backport_pr_number" "$TMPDIR_WORK/stale_data.json")
    bp_target=$(jq -r ".[$group_idx].backport_prs[$bp_idx].target_branch" "$TMPDIR_WORK/stale_data.json")
    bp_age_hours=$(jq -r ".[$group_idx].backport_prs[$bp_idx].age_hours" "$TMPDIR_WORK/stale_data.json")
    bp_age_days=$(jq -r ".[$group_idx].backport_prs[$bp_idx].age_days" "$TMPDIR_WORK/stale_data.json")
    bp_is_draft=$(jq -r ".[$group_idx].backport_prs[$bp_idx].is_draft" "$TMPDIR_WORK/stale_data.json")

    # Get assignees as space-separated list
    bp_assignees=$(jq -r ".[$group_idx].backport_prs[$bp_idx].assignees // [] | .[]" "$TMPDIR_WORK/stale_data.json" 2>/dev/null || true)
    orig_assignees=$(jq -r ".[$group_idx].original_pr_assignees // [] | .[]" "$TMPDIR_WORK/stale_data.json" 2>/dev/null || true)

    # Determine who to mention: backport PR assignees > original PR author > original PR assignees
    mentions=""
    if [[ -n "$bp_assignees" ]]; then
      while IFS= read -r assignee; do
        [[ -n "$assignee" ]] && mentions="${mentions}@${assignee} "
      done <<< "$bp_assignees"
    elif [[ "$orig_author" != "unknown" && ! "$orig_author" =~ ^(backport-action|app/.*)$ ]]; then
      mentions="@${orig_author} "
    elif [[ -n "$orig_assignees" ]]; then
      while IFS= read -r assignee; do
        [[ -n "$assignee" ]] && mentions="${mentions}@${assignee} "
      done <<< "$orig_assignees"
    fi
    mentions=$(echo "$mentions" | xargs)

    # Format age display
    if [[ "$bp_age_days" -gt 0 ]]; then
      bp_age_hours_mod=$((bp_age_hours % 24))
      age_display="${bp_age_days}d ${bp_age_hours_mod}h"
    else
      age_display="${bp_age_hours}h"
    fi

    # Build draft warning
    draft_note=""
    if [[ "$bp_is_draft" == "true" ]]; then
      draft_note=$'\n\n⚠️ This PR is in **draft** state, likely due to merge conflicts that need manual resolution.'
    fi

    # Check for existing bot comment — delete and recreate if older than RENOTIFY_AFTER_DAYS
    existing_comment_data=$(gh api --paginate "repos/${repo}/issues/${bp_num}/comments" \
      -q ".[] | select(.body | contains(\"${COMMENT_MARKER}\"))" 2>/dev/null | jq -s 'if length > 0 then .[0] else null end' || echo "null")

    if [[ "$existing_comment_data" != "null" ]]; then
      comment_id=$(echo "$existing_comment_data" | jq -r '.id')
      comment_created=$(echo "$existing_comment_data" | jq -r '.created_at')
      comment_timestamp=$("$DATE_PROGRAM" -d "$comment_created" +%s 2>/dev/null || echo "0")
      comment_age_seconds=$((current_timestamp - comment_timestamp))

      if [[ "$comment_age_seconds" -lt "$renotify_threshold_seconds" ]]; then
        echo "⏭️  PR #${bp_num}: Notified $((comment_age_seconds / 3600))h ago (< ${RENOTIFY_AFTER_DAYS}d), skipping" >&2
        skipped_count=$((skipped_count + 1))
        continue
      fi

      # Delete the old comment so the new one triggers notifications
      if [[ "$DRY_RUN" == "true" ]]; then
        echo "🔄 DRY-RUN: Would delete old comment (id: $comment_id, age: $((comment_age_seconds / 86400))d) and recreate on PR #${bp_num}" >&2
      else
        echo "🔄 Deleting old bot comment on PR #${bp_num} (age: $((comment_age_seconds / 86400))d)..." >&2
        gh api -X DELETE "repos/${repo}/issues/comments/${comment_id}" 2>/dev/null || true
      fi
      updated_count=$((updated_count + 1))
    fi

    # Build comment for the backport PR
    comment_body="${COMMENT_MARKER}
⏰ **Stale Backport PR** — open for **${age_display}**

${mentions:+Hey ${mentions}! }This backport PR targeting \`${bp_target}\` has been open for **${age_display}** without being merged.

📌 **Original PR:** #${orig_num} — ${orig_title}

**Please take action:**
- ✅ **Merge** this PR if it's ready
- 🔧 **Resolve conflicts** if it's in draft/conflicted state
- ❌ **Close** this PR if the backport is no longer needed
${draft_note}

---
<sub>🤖 This comment was added automatically by the stale backport tracker. • <a href="${WORKFLOW_RUN_URL}">View workflow run</a></sub>"

    if [[ "$DRY_RUN" == "true" ]]; then
      echo "🔍 DRY-RUN: Would comment on PR #${bp_num} (${bp_target}, open ${age_display})" >&2
      echo "--- comment body ---" >&2
      echo "$comment_body" >&2
      echo "--- end ---" >&2
    else
      echo "💬 Commenting on PR #${bp_num} (${bp_target}, open ${age_display})..." >&2

      if gh pr comment "$bp_num" --body "$comment_body" --repo "$repo" >/dev/null 2>&1; then
        echo "  ✅ Comment added" >&2
      else
        echo "  ❌ Failed to add comment" >&2
      fi

      # Add stale-backport label
      gh pr edit "$bp_num" --add-label "$STALE_LABEL" --repo "$repo" 2>/dev/null || true
    fi

    notified_count=$((notified_count + 1))
  done

  # Optionally notify on the original PR with a summary of all its stale backports
  if [[ "$NOTIFY_ORIGINAL" == "true" && "$orig_num" != "null" ]]; then
    # Build summary table using jq (safe from shell escaping)
    bp_summary=$(jq -r ".[$group_idx].backport_prs[] |
      \"| #\\(.backport_pr_number) | \`\\(.target_branch)\` | \\(.age_hours)h | \\(if .is_draft then \"⚠️ draft\" else \"\" end) |\"" \
      "$TMPDIR_WORK/stale_data.json")

    orig_comment="${COMMENT_MARKER}
⏰ **Stale Backport Summary** for this PR

${mentions:+cc ${mentions}}

| Backport PR | Target Branch | Open Since | Status |
|-------------|---------------|------------|--------|
${bp_summary}

Please review and merge/close these backport PRs.

---
<sub>🤖 Auto-generated by the stale backport tracker. • <a href="${WORKFLOW_RUN_URL}">View workflow run</a></sub>"

    # Check for existing comment on original PR
    existing_orig_comment=$(gh api --paginate "repos/${repo}/issues/${orig_num}/comments" \
      -q ".[] | select(.body | contains(\"${COMMENT_MARKER}\"))" 2>/dev/null | jq -s 'length' || echo "0")

    if [[ "$existing_orig_comment" -eq 0 ]]; then
      if [[ "$DRY_RUN" == "true" ]]; then
        echo "🔍 DRY-RUN: Would comment on original PR #${orig_num} with ${bp_count} stale backport(s)" >&2
        echo "--- comment body ---" >&2
        echo "$orig_comment" >&2
        echo "--- end ---" >&2
      else
        echo "💬 Commenting on original PR #${orig_num}..." >&2
        gh pr comment "$orig_num" --body "$orig_comment" --repo "$repo" >/dev/null 2>&1 || true
      fi
    fi
  fi
done < <(jq -c 'range(length) as $i | {index: $i, group: .[$i]}' "$TMPDIR_WORK/stale_data.json")

echo "📊 Notifications: ${notified_count} new, ${updated_count} re-notified, ${skipped_count} skipped (recently notified)" >&2
