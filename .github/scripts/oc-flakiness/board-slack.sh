#!/usr/bin/env bash
#
# Post or rewrite the OC flakiness fix agent's daily board message.
# Reads the message text on stdin and prints {"ts": ..., "channel": ...} on
# stdout -- `channel` being the conversation ID Slack resolved the name to.
# chat.update needs that ID: handed the channel NAME it answers
# channel_not_found, so a board posted by name would never update again.
#
#   board-slack.sh post   <channel>
#   board-slack.sh update <channel> <ts>
#
# Requires SLACK_BOT_USER_OAUTH_TOKEN. A Slack error is reported and exits
# non-zero so the caller can decide whether it is fatal -- for a fix run it is
# not: the verdict is already on the state branch and in the run summary.

set -euo pipefail

mode="${1:?mode is required}"
channel="${2:?channel is required}"
text="$(cat)"

case "$mode" in
  post)
    method=chat.postMessage
    payload=$(jq -nc --arg channel "$channel" --arg text "$text" \
      '{channel: $channel, text: $text, mrkdwn: true}')
    ;;
  update)
    method=chat.update
    payload=$(jq -nc --arg channel "$channel" --arg ts "${3:?ts is required}" --arg text "$text" \
      '{channel: $channel, ts: $ts, text: $text, mrkdwn: true}')
    ;;
  *)
    echo "board-slack.sh: mode must be post or update" >&2
    exit 2
    ;;
esac

response=$(curl -sS -X POST "https://slack.com/api/${method}" \
  -H "Authorization: Bearer ${SLACK_BOT_USER_OAUTH_TOKEN:?}" \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d "$payload" || echo '{}')

if [[ "$(jq -r '.ok // false' <<< "$response")" != "true" ]]; then
  echo "::warning::${method} failed: $(jq -r '.error // "no response"' <<< "$response")" >&2
  exit 1
fi
jq -c '{ts: (.ts // ""), channel: (.channel // "")}' <<< "$response"
