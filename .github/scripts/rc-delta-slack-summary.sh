#!/bin/bash

# Computes a deterministic delta summary (merged PRs + direct commits) between the
# previous RC tag and the current RC tag, and prints a Slack Block Kit payload to stdout.
#
# Design (issue #40007 — deterministic core only, no AI/agentic categorization):
#   * git ancestry is authoritative: the commit set is `git log PREV_TAG..RC_VERSION`
#     computed locally — zero GitHub API calls to enumerate the delta.
#   * commit -> PR linking uses ONE batched GraphQL query (associatedPullRequests keyed
#     by commit OID, chunked), not one REST call per commit. This reliably collapses the
#     individual (non-squashed) backport commits on release branches back into their
#     parent PRs — commit-message regex alone misses the majority of them.
#   * commits with no associated PR are still listed under "Other changes" — nothing is
#     silently dropped.
#
# The previous RC tag is resolved upstream by the `previous-version` infra action and
# passed in as PREV_VERSION. If PREV_VERSION is not an RC tag for the same prefix (i.e.
# this is the first candidate — RC1), a fixed "no delta" note is printed instead.
#
# Requires: a checkout with fetch-depth: 0 (full tag history), gh CLI, jq.
#
# Env vars:
#   RC_VERSION    - required. RC tag to summarize, e.g. "8.9.0-alpha3-rc2" or "8.9.0-rc2"
#   PREV_VERSION  - required. Previous version from the previous-version action (may be a
#                   non-RC fallback such as "8.8.0" for a first candidate).
#   REPOSITORY    - optional. owner/repo, defaults to camunda/camunda
#   GH_TOKEN      - required by gh CLI for the GraphQL enrichment call

set -euo pipefail

RC_VERSION="${RC_VERSION:?RC_VERSION is required}"
PREV_VERSION="${PREV_VERSION:?PREV_VERSION is required}"
REPOSITORY="${REPOSITORY:-camunda/camunda}"
OWNER="${REPOSITORY%%/*}"
REPO="${REPOSITORY##*/}"

# RC tags follow "<prefix>-rc<N>", e.g. "8.9.0-alpha3-rc2" -> prefix "8.9.0-alpha3".
if [[ ! "$RC_VERSION" =~ ^(.+)-rc([0-9]+)$ ]]; then
  echo "RC_VERSION '$RC_VERSION' does not match the expected '<prefix>-rc<N>' format" >&2
  exit 1
fi
PREFIX="${BASH_REMATCH[1]}"

# First-candidate guard: only compute a delta when PREV_VERSION is a real RC tag for the
# SAME prefix. For RC1 (of a release or of an alpha stage) the previous-version action
# returns a non-RC baseline (e.g. "8.8.0" or the prior alpha), which we deliberately never
# diff against — post a fixed note instead.
if [[ "$PREV_VERSION" != "${PREFIX}-rc"[0-9]* ]]; then
  jq -n --arg version "$RC_VERSION" '{
    blocks: [
      {
        type: "section",
        text: {
          type: "mrkdwn",
          text: ("*RC delta summary — " + $version + "* :sparkles-rainbow:\n_First candidate — full regression expected. No potion to brew yet!_ :cat-wizard:")
        }
      }
    ]
  }'
  exit 0
fi

PREV_TAG="$PREV_VERSION"
COMPARE_URL="https://github.com/${REPOSITORY}/compare/${PREV_TAG}...${RC_VERSION}"

TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

# Commit set (authoritative), newest first, as {sha, subject} objects — zero API calls.
# Each commit is emitted as two lines (%H then %s) rather than a tab-delimited pair:
# a commit subject may legally contain a tab, which would corrupt a "\t"-split, but it
# can never contain a newline (it's only the first line of the message).
# Release due-diligence no-ops are excluded from the delta entirely: the maven-release-plugin
# commits (prepare release / next dev iteration) AND their `Revert "[maven-release-plugin] …"`
# counterparts, which the RC-rerun mechanics create and which map only to the release-branch PR.
git log --format=$'%H%n%s' "${PREV_TAG}..${RC_VERSION}" \
  | jq -R -s 'split("\n")
      | (if .[-1] == "" then .[0:-1] else . end)
      | [range(0; length; 2) as $i | {sha: .[$i], subject: (.[$i + 1] // "")}]
      | map(select(.subject | test("^(\\[maven-release-plugin\\]|Revert \"\\[maven-release-plugin\\])") | not))' \
  > "$TMPDIR_WORK/commits.json"

TOTAL_COMMITS=$(jq 'length' "$TMPDIR_WORK/commits.json")

# Enrich commits -> PRs via batched GraphQL. The commit OID doubles as the field alias
# (prefixed with "_" so it's a valid GraphQL name), so results map back without bookkeeping.
echo '{}' > "$TMPDIR_WORK/prmap.json"

if (( TOTAL_COMMITS > 0 )); then
  mapfile -t SHAS < <(jq -r '.[].sha' "$TMPDIR_WORK/commits.json")

  CHUNK_SIZE=100
  for (( start = 0; start < ${#SHAS[@]}; start += CHUNK_SIZE )); do
    query="query{repository(owner:\"${OWNER}\",name:\"${REPO}\"){"
    for sha in "${SHAS[@]:start:CHUNK_SIZE}"; do
      query+="_${sha}:object(oid:\"${sha}\"){... on Commit{associatedPullRequests(first:1){nodes{number title url}}}}"
    done
    query+="}}"

    gh api graphql -f query="$query" --jq '.data.repository' > "$TMPDIR_WORK/chunk.json"
    jq -s '.[0] * .[1]' "$TMPDIR_WORK/prmap.json" "$TMPDIR_WORK/chunk.json" > "$TMPDIR_WORK/prmap_tmp.json"
    mv "$TMPDIR_WORK/prmap_tmp.json" "$TMPDIR_WORK/prmap.json"
  done
fi

# Attach each commit's associated PR (if any), then split into deduplicated PRs and
# "other" direct commits that resolved to no PR.
jq -n \
  --slurpfile commits "$TMPDIR_WORK/commits.json" \
  --slurpfile prmap "$TMPDIR_WORK/prmap.json" \
  '
  ($commits[0]) as $commits |
  ($prmap[0]) as $prmap |
  ($commits | map(. + {pr: ($prmap["_" + .sha].associatedPullRequests.nodes[0] // null)})) as $enriched |
  {
    prs: ($enriched | map(select(.pr != null) | .pr) | unique_by(.number) | sort_by(-.number)),
    others: ($enriched | map(select(.pr == null) | {sha, subject}))
  }
  ' > "$TMPDIR_WORK/summary.json"

PR_COUNT=$(jq '.prs | length' "$TMPDIR_WORK/summary.json")
OTHER_COUNT=$(jq '.others | length' "$TMPDIR_WORK/summary.json")

PR_TRUNCATE_AT=25
OTHER_TRUNCATE_AT=10

# Strip leading "[Backport …]" prefixes (including repeated/nested ones such as
# "[Backport release-8.9.0] [Backport stable/8.9] ") — they're release-branch plumbing,
# not signal, and just add noise to the digest.
pr_lines=$(jq -r --argjson limit "$PR_TRUNCATE_AT" \
  '.prs[:$limit][]
   | (.title | gsub("^(\\s*\\[Backport[^\\]]*\\]\\s*)+"; "")) as $title
   | "- <\(.url)|#\(.number)> \($title)"' "$TMPDIR_WORK/summary.json")
if [[ -z "$pr_lines" ]]; then
  pr_lines="_No merged PRs found in this range._"
fi
pr_remaining=$(( PR_COUNT > PR_TRUNCATE_AT ? PR_COUNT - PR_TRUNCATE_AT : 0 ))
if (( pr_remaining > 0 )); then
  pr_lines="${pr_lines}"$'\n'":party-wizard: _…and ${pr_remaining} more bubbling in the cauldron — see <${COMPARE_URL}|the full compare>_"
fi

other_lines=""
if (( OTHER_COUNT > 0 )); then
  other_lines=$(jq -r --argjson limit "$OTHER_TRUNCATE_AT" \
    '.others[:$limit][] | "- `\(.sha[0:7])` \(.subject)"' "$TMPDIR_WORK/summary.json")
  other_remaining=$(( OTHER_COUNT > OTHER_TRUNCATE_AT ? OTHER_COUNT - OTHER_TRUNCATE_AT : 0 ))
  if (( other_remaining > 0 )); then
    other_lines="${other_lines}"$'\n'"…and ${other_remaining} more"
  fi
fi

jq -n \
  --arg version "$RC_VERSION" \
  --arg prev "$PREV_TAG" \
  --arg compare "$COMPARE_URL" \
  --arg pr_lines "$pr_lines" \
  --arg other_lines "$other_lines" \
  --argjson pr_count "$PR_COUNT" \
  --argjson other_count "$OTHER_COUNT" \
  '{
    blocks: (
      [
        {
          type: "section",
          text: {
            type: "mrkdwn",
            text: ("*RC delta summary — " + $version + "* :sparkles-rainbow:\n_A little magic has happened since `" + $prev + "`: " + ($pr_count | tostring) + " PR(s) stirred into the cauldron!_ :cat-wizard:")
          }
        },
        {
          type: "section",
          text: { type: "mrkdwn", text: ("*:meow-merged: Merged PRs:*\n" + $pr_lines) }
        }
      ]
      + (if $other_count > 0 then
          [{ type: "section", text: { type: "mrkdwn", text: ("*:hammer_and_wrench: Other changes (loose commits):*\n" + $other_lines) } }]
        else [] end)
      + [{ type: "context", elements: [{ type: "mrkdwn", text: (":party-wizard: <" + $compare + "|Peek at the full compare>") }] }]
    )
  }'
