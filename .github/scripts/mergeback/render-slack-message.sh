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
# ships.
set -euo pipefail

triage_json_file="${1:-}"
run_url="${RUN_URL:-}"
footer="Triggered by GitHub Actions${run_url:+ • <${run_url}|view workflow run>}"

doc="$(if [ -n "$triage_json_file" ]; then cat "$triage_json_file"; else cat; fi)"

jq -c --arg footer "$footer" --arg run_url "$run_url" \
  --argjson max_paths 20 --argjson max_commits 5 '
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
  ) as $truncated
  | { text: (.verdict | slack_safe),
      blocks: (
        [ { type: "header",
            text: { type: "plain_text", text: ":human-robot-heart: Merge-back needs a human", emoji: true } },
          { type: "section",
            text: { type: "mrkdwn", text: ("*`" + (.release_ref | slack_safe) + "`* → *`" + (.target_ref | slack_safe) + "`*\n:cta: " + (.verdict | slack_safe)) } } ]
        + actionable_list(":blob_detective:"; "Source changes maybe missing on target or release"; .source_missing)
        + actionable_list(":package:"; "Dependency/build changes maybe missing on target"; .version_build_gap)
        + actionable_list(":mag:"; "Conflicts needing manual review — no commit to attribute; resolve at merge-back"; (.needs_review // []))
        + (if $truncated
           then [ { type: "context", elements: [ { type: "mrkdwn",
                  text: (":warning: Output truncated to fit Slack limits — see the "
                         + (if $run_url != "" then "<" + $run_url + "|workflow run>" else "workflow run" end)
                         + " for the complete list.") } ] } ]
           else [] end)
        + [ { type: "context", elements: [ { type: "mrkdwn", text: $footer } ] } ]
      ) }' <<< "$doc"
