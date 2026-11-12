#!/usr/bin/env bash
#
# Read-modify-write the daily Slack board of the OC flakiness fix agent on the
# state branch, and print the resulting board to stdout.
#
# Every dispatched run finishes on its own schedule and they overlap, so the
# board cannot live in a job output or an artifact: it is a file on
# oc-flakiness-state and the push is what serialises the writers. A push that
# loses the race is retried against the file the winner left, so no row is
# dropped -- the same loop the ledger uses.
#
# Usage:
#   BOARD_JSON='{...}' board-store.sh init <date>
#   board-store.sh set <date> <fp> <status> <detail> <pr_url> <run_url> <rate>
#
# Required environment: TOKEN, REPO, STATE_BRANCH, and for `set` the directory
# holding board.mjs as $SCRIPT_DIR (defaults to this script's own directory).

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

for attempt in 1 2 3 4 5; do
  if git -C "$state" fetch -q --depth 1 origin "$STATE_BRANCH" 2>/dev/null; then
    git -C "$state" checkout -q -B "$STATE_BRANCH" FETCH_HEAD
  else
    git -C "$state" checkout -q --orphan "$STATE_BRANCH"
    git -C "$state" rm -rq --cached . 2>/dev/null || true
  fi
  mkdir -p "$(dirname "${state}/${file}")"

  case "$mode" in
    init)
      printf '%s' "${BOARD_JSON:?BOARD_JSON is required for init}" > "${state}/${file}"
      # One board a day would otherwise accumulate for the life of the branch.
      # A month is well past the point where any run is still updating one.
      cutoff=$(date -u -d '30 days ago' +%Y-%m-%d)
      for old in "${state}"/daily/*.json; do
        [[ -e "$old" ]] || continue
        day=$(basename "$old" .json)
        if [[ "$day" < "$cutoff" ]]; then
          git -C "$state" rm -q "daily/${day}.json"
        fi
      done
      ;;
    set)
      if [[ ! -f "${state}/${file}" ]]; then
        # The run was dispatched by hand against a board that was never
        # created, or the board has already been pruned. Nothing to update,
        # and failing here would fail an otherwise successful fix run.
        echo "::notice::no board at ${file}; skipping the Slack update" >&2
        exit 3
      fi
      node "${script_dir}/board.mjs" --set "${state}/${file}" \
        --fp "${3:?}" --status "${4:?}" --detail "${5-}" \
        --pr "${6-}" --run "${7-}" --rate "${8-}" > "${state}/${file}.tmp"
      mv "${state}/${file}.tmp" "${state}/${file}"
      ;;
    *)
      echo "board-store.sh: mode must be init or set" >&2
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
    cat "${state}/${file}"
    exit 0
  fi
  echo "::notice::board push raced another run (attempt ${attempt}); retrying" >&2
  sleep $((attempt * 5))
done

echo "::error::could not update the daily board at ${file}" >&2
exit 1
