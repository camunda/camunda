#!/usr/bin/env bash
# Render ONE triage JSON document (from triage-mergeback.sh) into a Slack Block Kit message body.
#
# Pure presentation, read-only: reads the triage JSON from stdin or a file, prints the Slack POST
# body to stdout. Unit-tested in mergeback.bats and reused for dry-run rendering into the run
# summary.
#
#   render-slack-message.sh [triage_json_file]   # reads stdin if no file given
#   RUN_URL   (optional) link to the workflow run; used in the footer + truncation notice.
#
# Safety: every piece of untrusted Git metadata (path, owner, commit subject/author) goes through
# `slack_safe`, which neutralises the `< > &` that open Slack's link/mention syntax (<!here>, <@U…>).
# Backticks are cosmetic, not a safety mechanism.
#
# Limits: Slack rejects >50 blocks or a section >3000 chars — which would drop the alert for exactly
# the biggest conflicts. So we cap paths per bucket (max_paths) and commits per path (max_commits),
# clip each section, and apply a global block budget (max_blocks) across all buckets. When anything
# is cut, a truncation notice points to the run log, which holds the full untruncated triage.
set -euo pipefail

triage_json_file="${1:-}"
run_url="${RUN_URL:-}"
footer="Triggered by GitHub Actions${run_url:+ • <${run_url}|view workflow run>}"

doc="$(if [ -n "$triage_json_file" ]; then cat "$triage_json_file"; else cat; fi)"

# Blocks that are always present regardless of conflict volume: header + intro section + truncation
# notice + footer. Reserved out of the 50-block budget so the actionable sections can never push the
# message over Slack's limit.
reserved_blocks=4

jq -c --arg footer "$footer" --arg run_url "$run_url" \
  --argjson max_paths 20 --argjson max_commits 5 --argjson max_blocks 50 \
  --argjson reserved_blocks "$reserved_blocks" '
  def slack_safe: gsub("&";"&amp;") | gsub("<";"&lt;") | gsub(">";"&gt;");
  def clip(n): if (. | length) > n then (.[:n] + "…") else . end;

  # Full (pre-clip) mrkdwn body for one actionable path. Its own function so the SAME text drives
  # both rendering and the truncation check below.
  def path_body($max_commits):
    . as $p
    | ( $p.commits[:$max_commits]
        | map("   - *Ref:* `" + (.sha | slack_safe) + "` *Author:* `" + (.author | slack_safe)
              + "` *Message:* " + (.subject | slack_safe)
              + (if (.missing_on // "") != "" then "  _(missing on " + (.missing_on | slack_safe) + ")_" else "" end))
        | join("\n") ) as $commit_lines
    | ( "- `" + ($p.path | slack_safe) + "`"
        + (if $p.owner != "" then "  — owner `" + ($p.owner | slack_safe) + "`" else "" end)
        + "\n" + $commit_lines
        + (if ($p.commits | length) > $max_commits
           then "\n   - …(" + (($p.commits | length) - $max_commits | tostring) + " more commit(s))"
           else "" end) );

  def actionable_list(emoji; title; arr):
    if (arr | length) > 0 then
      [ { type: "section",
          text: { type: "mrkdwn",
                  text: (emoji + " *" + title + "*  (" + (arr | length | tostring) + ")"
                         + (if (arr | length) > $max_paths
                            then "  — showing first " + ($max_paths | tostring) else "" end)) } } ]
      + ( arr[:$max_paths] | map(
          path_body($max_commits)
          | clip(2900)
          | { type: "section", text: { type: "mrkdwn", text: . } } ) )
    else [] end;

  # Fire the truncation notice whenever anything is cut — too many paths, too many commits, or a body
  # clipped by clip(2900). Checked on the same path_body text that renders.
  ( ((.source_missing | length) > $max_paths)
    or ((.version_build_gap | length) > $max_paths)
    or (((.needs_review // []) | length) > $max_paths)
    or ([.source_missing[], .version_build_gap[]] | any(.commits | length > $max_commits))
    or ([ (.source_missing[], .version_build_gap[]) | path_body($max_commits) | length > 2900 ] | any)
  ) as $content_truncated

  # One global block budget: the per-bucket cap cannot see the cross-bucket total, so concatenate all
  # actionable sections and clip to what the fixed chrome ($reserved_blocks) leaves, keeping the
  # whole message within max_blocks.
  | ( actionable_list(":blob_detective:"; "Source changes maybe missing on target or release"; .source_missing)
      + actionable_list(":package:"; "Dependency/build changes maybe missing on target"; .version_build_gap)
      + actionable_list(":mag:"; "Conflicts needing manual review — no commit to attribute; resolve at merge-back"; (.needs_review // [])) ) as $actionable
  | ($max_blocks - $reserved_blocks) as $actionable_budget
  | ($actionable | length > $actionable_budget) as $block_overflow
  # $truncated also fires on a global block clip, so the notice ships whenever ANYTHING is cut.
  | ($content_truncated or $block_overflow) as $truncated
  | { text: (.verdict | slack_safe),
      blocks: (
        [ { type: "header",
            text: { type: "plain_text", text: ":human-robot-heart: Merge-back needs a human", emoji: true } },
          { type: "section",
            text: { type: "mrkdwn", text: ("*`" + (.release_ref | slack_safe) + "`* → *`" + (.target_ref | slack_safe) + "`*\n:cta: " + (.verdict | slack_safe)
              + "\nReview both branches: act now on a missing required change; otherwise record the later merge resolution or escalate to the owner.") } } ]
        + $actionable[:$actionable_budget]
        + (if $truncated
           then [ { type: "context", elements: [ { type: "mrkdwn",
                  text: (":warning: Too many changes to fit one Slack message — this is a partial list. The complete, untruncated detail is in the "
                         + (if $run_url != "" then "<" + $run_url + "|GitHub Actions run>" else "GitHub Actions run" end)
                         + " log.") } ] } ]
           else [] end)
        + [ { type: "context", elements: [ { type: "mrkdwn", text: $footer } ] } ]
      ) }' <<< "$doc"
