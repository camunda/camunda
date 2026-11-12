#!/usr/bin/env bash
#
# Keep the OC flakiness fix agent's daily Slack board on the state branch and
# in the channel, and print the resulting board to stdout.
#
# Every dispatched run finishes on its own schedule and they overlap, so the
# board cannot live in a job output or an artifact: it is a file on
# oc-flakiness-state and the push is what serialises the writers. A push that
# loses the race is retried against the file the winner left, so no row is
# dropped -- the same loop the ledger uses.
#
# Pushing in order is not enough on its own, though. Two writers can both
# commit and then publish in the opposite order, and the loser's older snapshot
# would put the winner's finished row back to "still running". Each write
# therefore bumps the board's `rev`, and `publish` only sends a board that is
# still the newest on the branch -- re-reading and re-sending until the branch
# stops moving under it.
#
# Usage:
#   BOARD_JSON='{...}' board-store.sh init    <date>   # create, or merge into today's
#                      board-store.sh ts      <date> <ts> <channel_id>
#                      board-store.sh set     <date> <fp> <status> <detail> <pr> <run> <rate>
#                      board-store.sh publish <date>
#
# `init` and `set` only persist; `publish` is the one that talks to Slack, so a
# caller that has nothing new to show can skip it.
#
# Required environment: TOKEN, REPO, STATE_BRANCH; SLACK_BOT_USER_OAUTH_TOKEN
# for `publish`. SCRIPT_DIR defaults to this script's own directory.

set -euo pipefail

mode="${1:?mode is required}"
date_key="${2:?date is required}"
script_dir="${SCRIPT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
file="daily/${date_key}.json"

state="$(mktemp -d)"
git init -q "$state"
git -C "$state" config user.name "qa-processes[bot]"
git -C "$state" config user.email "qa-processes[bot]@users.noreply.github.com"
git -C "$state" remote add origin "https://x-access-token:${TOKEN}@github.com/${REPO}.git"

# Replace the working copy with the branch as it is right now.
sync_state() {
  if git -C "$state" fetch -q --depth 1 origin "$STATE_BRANCH" 2>/dev/null; then
    git -C "$state" checkout -q -B "$STATE_BRANCH" FETCH_HEAD
  else
    git -C "$state" checkout -q --orphan "$STATE_BRANCH"
    git -C "$state" rm -rq --cached . 2>/dev/null || true
  fi
  mkdir -p "$(dirname "${state:?}/${file}")"
}

# Send the board to Slack, but only while it is the newest one on the branch:
# a board that has been superseded is already included in the newer one, and
# re-sending it would undo rows the other writer has just published. Three
# rounds is plenty -- each one is another writer finishing in the same second.
publish() {
  for _ in 1 2 3; do
    sync_state
    [[ -f "${state}/${file}" ]] || return 0
    local ts channel rev after
    ts=$(jq -r '.ts // ""' "${state}/${file}")
    channel=$(jq -r '.channel // ""' "${state}/${file}")
    rev=$(jq -r '.rev // 0' "${state}/${file}")
    if [[ -z "$ts" || -z "$channel" ]]; then
      echo "::notice::board ${date_key} has no message to update yet" >&2
      return 0
    fi
    # A Slack outage must not fail the run: the verdict is already on the
    # branch, in the run summary and -- for a fix -- in the PR.
    node "${script_dir}/board.mjs" --render "${state}/${file}" \
      | bash "${script_dir}/board-slack.sh" update "$channel" "$ts" > /dev/null \
      || echo "::warning::could not rewrite the daily board message" >&2
    sync_state
    after=$(jq -r '.rev // 0' "${state}/${file}" 2>/dev/null || echo "$rev")
    if [[ "$after" == "$rev" ]]; then
      return 0
    fi
  done
  return 0
}

if [[ "$mode" == "publish" ]]; then
  publish
  cat "${state}/${file}" 2>/dev/null || true
  exit 0
fi

for attempt in 1 2 3 4 5; do
  sync_state

  case "$mode" in
    init)
      if [[ -f "${state}/${file}" ]]; then
        # A re-run of the pick on the same day: keep the message that is
        # already in the channel and the rows whose runs are reporting to it,
        # and add only what this pick dispatched on top.
        printf '%s' "${BOARD_JSON:?BOARD_JSON is required for init}" \
          | jq -c '.rows' > "${state}/targets.tmp"
        node "${script_dir}/board.mjs" --merge "${state}/${file}" \
          --targets "${state}/targets.tmp" > "${state}/${file}.tmp"
        rm -f "${state}/targets.tmp"
      else
        printf '%s' "${BOARD_JSON:?BOARD_JSON is required for init}" > "${state}/${file}.tmp"
        # One board a day would otherwise accumulate for the life of the
        # branch. A month is well past the point where a run still updates one.
        cutoff=$(date -u -d '30 days ago' +%Y-%m-%d)
        for old in "${state}"/daily/*.json; do
          [[ -e "$old" ]] || continue
          day=$(basename "$old" .json)
          if [[ "$day" < "$cutoff" ]]; then
            git -C "$state" rm -q "daily/${day}.json"
          fi
        done
      fi
      mv "${state}/${file}.tmp" "${state}/${file}"
      ;;
    ts)
      jq -c --arg ts "${3:?}" --arg channel "${4:?}" \
        '.ts = $ts | .channel = $channel | .rev = ((.rev // 0) + 1)' \
        "${state}/${file}" > "${state}/${file}.tmp"
      mv "${state}/${file}.tmp" "${state}/${file}"
      ;;
    set)
      if [[ ! -f "${state}/${file}" ]]; then
        # Dispatched by hand against a board that was never created, or one
        # already pruned. Failing here would fail an otherwise good fix run.
        echo "::notice::no board at ${file}; skipping the Slack update" >&2
        exit 3
      fi
      node "${script_dir}/board.mjs" --set "${state}/${file}" \
        --fp "${3:?}" --status "${4:?}" --detail "${5-}" \
        --pr "${6-}" --run "${7-}" --rate "${8-}" > "${state}/${file}.tmp"
      mv "${state}/${file}.tmp" "${state}/${file}"
      ;;
    *)
      echo "board-store.sh: mode must be init, ts, set or publish" >&2
      exit 2
      ;;
  esac

  git -C "$state" add "$file"
  if git -C "$state" diff --cached --quiet; then
    cat "${state}/${file}"
    exit 0
  fi
  git -C "$state" commit -qm "board: ${mode} ${date_key}${3:+ ${3}}"
  if git -C "$state" push -q origin "HEAD:refs/heads/${STATE_BRANCH}"; then
    if [[ "$mode" == "set" ]]; then
      publish
    fi
    cat "${state}/${file}"
    exit 0
  fi
  echo "::notice::board push raced another run (attempt ${attempt}); retrying" >&2
  sleep $((attempt * 5))
done

echo "::error::could not update the daily board at ${file}" >&2
exit 1
