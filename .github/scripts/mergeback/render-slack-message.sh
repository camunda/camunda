#!/usr/bin/env bash
# Render ONE triage JSON document (from triage-mergeback.sh) into a Slack Block Kit message body.
#
# Pure presentation, read-only: no git, no network, no side effects. Reads the triage JSON (the
# single source of truth) from stdin or a file, prints the Slack POST body to stdout. Extracted
# from the inline workflow jq so it can be unit-tested (mergeback.bats) and dry-run rendered into
# $GITHUB_STEP_SUMMARY without posting.
#
#   render-slack-message.sh [triage_json_file]      # reads stdin if no file given
#   RUN_URL   (optional) link to the workflow run; used in the footer + truncation notice.
#
# SAFETY (no pings / no injection): every piece of untrusted Git metadata (path, owner, commit
# subject/author) goes through `slack_safe`, which neutralises the `< > &` that open Slack's mrkdwn
# link/mention syntax (<!here>, <@U…>). Backticks are cosmetic, NOT a safety mechanism — a value
# may itself contain a backtick.
#
# LIMITS: Slack rejects >50 blocks or a section >3000 chars — which would drop the ONLY
# notification for exactly the biggest conflicts. Cap paths per list (max_paths) and commits per
# path (max_commits), clip each section, and append a truncation notice — a bounded message always
# ships. max_paths alone is a PER-bucket cap: three fully-populated buckets each render up to
# (1 header + max_paths) sections, so their sum can blow the 50-block limit even when no single
# bucket overflows. A single GLOBAL block budget (max_blocks) clips the concatenated actionable
# sections after the per-bucket caps, so the total message can never exceed 50 blocks. When any clip
# happens the notice points to the workflow run, whose log prints the full, untruncated triage doc
# for every pair — so nothing actionable is ever lost, only relocated.
set -euo pipefail

triage_json_file="${1:-}"
run_url="${RUN_URL:-}"
footer="Triggered by GitHub Actions${run_url:+ • <${run_url}|view workflow run>}"

doc="$(if [ -n "$triage_json_file" ]; then cat "$triage_json_file"; else cat; fi)"

jq -c --arg footer "$footer" --arg run_url "$run_url" \
  --argjson max_paths 20 --argjson max_commits 5 --argjson max_blocks 50 '
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

  # $truncated must fire whenever ANYTHING is cut — too many paths, too many commits, OR a single
  # body clipped by clip(2900). Checked on the same path_body text that renders, so a long-subject
  # clip cannot hide the notice.
  ( ((.source_missing | length) > $max_paths)
    or ((.version_build_gap | length) > $max_paths)
    or (((.needs_review // []) | length) > $max_paths)
    or ([.source_missing[], .version_build_gap[]] | any(.commits | length > $max_commits))
    or ([ (.source_missing[], .version_build_gap[]) | path_body($max_commits) | length > 2900 ] | any)
  ) as $content_truncated

  # ONE global block budget. The per-bucket max_paths cap does not see the cross-bucket total, so
  # concatenate every actionable section first, then clip the whole run to whatever the fixed chrome
  # leaves. Reserve 4 chrome blocks (header + intro section + truncation notice + footer) so the
  # final message is header+intro (2) + actionable (≤max_blocks-4) + notice (≤1) + footer (1) ≤ max_blocks.
  | ( actionable_list(":blob_detective:"; "Source changes maybe missing on target or release"; .source_missing)
      + actionable_list(":package:"; "Dependency/build changes maybe missing on target"; .version_build_gap)
      + actionable_list(":mag:"; "Conflicts needing manual review — no commit to attribute; resolve at merge-back"; (.needs_review // [])) ) as $actionable
  | ($max_blocks - 4) as $actionable_budget
  | ($actionable | length > $actionable_budget) as $block_overflow
  # $truncated also fires on a global block clip, so the notice ships whenever ANYTHING is cut.
  | ($content_truncated or $block_overflow) as $truncated
  | { text: (.verdict | slack_safe),
      blocks: (
        [ { type: "header",
            text: { type: "plain_text", text: ":human-robot-heart: Merge-back needs a human", emoji: true } },
          { type: "section",
            text: { type: "mrkdwn", text: ("*`" + (.release_ref | slack_safe) + "`* → *`" + (.target_ref | slack_safe) + "`*\n:cta: " + (.verdict | slack_safe)) } } ]
        + $actionable[:$actionable_budget]
        + (if $truncated
           then [ { type: "context", elements: [ { type: "mrkdwn",
                  text: (":warning: Too many changes to fit one Slack message — this is a partial list. The complete, untruncated detail is in the "
                         + (if $run_url != "" then "<" + $run_url + "|GitHub Actions run>" else "GitHub Actions run" end)
                         + " log.") } ] } ]
           else [] end)
        + [ { type: "context", elements: [ { type: "mrkdwn", text: $footer } ] } ]
      ) }' <<< "$doc"
