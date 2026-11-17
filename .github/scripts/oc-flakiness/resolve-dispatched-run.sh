#!/usr/bin/env bash
#
# Resolve the run a `gh workflow run` of the OC flakiness fix agent just
# created, so the daily Slack board can link each dispatched test to its run
# from the moment the board is posted rather than hours later at its verdict.
#
# `gh workflow run` returns nothing, and taking the newest run of the workflow
# would pick whichever dispatch happened to start last -- the pick fires up to
# three within a second of each other, and the janitor and the next day's pick
# share the same workflow file. The correlation is the run-name, which carries
# the version and the spec:
#
#   Flakiness fix — <version> <spec>
#
# A creation-time floor excludes a previous day's run for the same test, and
# CLAIMED exclues ids already handed out in this execution -- without it, a
# re-dispatch of the same spec in one pick would resolve to the same run twice.
#
# Prints the run URL on stdout, or nothing. Never fails the caller: a row
# without a link is worth far less than a failed dispatch.
#
# Inputs (env): REPO, VERSION, SPEC, SINCE_TS (ISO8601), CLAIMED (comma-
# separated run ids), ATTEMPTS (default 6), SLEEP_SECONDS (default 5).

set -uo pipefail

REPO="${REPO:?}"
ATTEMPTS="${ATTEMPTS:-6}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"
title="Flakiness fix — ${VERSION:?} ${SPEC:?}"

for _ in $(seq 1 "$ATTEMPTS"); do
  id=$(gh run list --repo "$REPO" \
        --workflow c8-orchestration-cluster-flakiness-fix.yml \
        --event workflow_dispatch --limit 50 \
        --json databaseId,displayTitle,createdAt 2>/dev/null |
    jq -r --arg t "$title" --arg since "${SINCE_TS:-}" --arg claimed "${CLAIMED:-}" '
      ($claimed | split(",")) as $taken
      | [ .[]
          | select(.displayTitle == $t)
          | select($since == "" or .createdAt >= $since)
          # Bind the id first: inside index() the input is $taken, so
          # .databaseId there would be read off the array and error out.
          | (.databaseId | tostring) as $id
          | select($taken | index($id) | not) ]
      | sort_by(.createdAt) | .[0].databaseId // ""
    ')
  if [[ -n "${id:-}" ]]; then
    printf '%s/%s/actions/runs/%s' "${GITHUB_SERVER_URL:-https://github.com}" "$REPO" "$id"
    exit 0
  fi
  sleep "$SLEEP_SECONDS"
done

echo "::notice::could not resolve the run dispatched for ${title}" >&2
