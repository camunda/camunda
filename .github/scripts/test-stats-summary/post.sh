#!/usr/bin/env bash
# Posts the Slack messages that build.sh wrote: the first one to the channel, the others as
# replies in its thread.
#
# Usage: post.sh <messages.json>
#
# Required env:
#   SLACK_BOT_TOKEN  bot token with chat:write, the bot must be able to post in the channel
#   SLACK_CHANNEL    channel ID
#
# Fails when the top-level message fails. A failed thread reply only logs a warning, so the
# other branches still get their reply.

set -euo pipefail

MESSAGES="${1:?usage: $0 <messages.json>}"
: "${SLACK_BOT_TOKEN:?}" "${SLACK_CHANNEL:?}"

# Args: message JSON, thread ts (empty for a top-level message). Prints the Slack API response.
post_message() {
  jq -c --arg channel "$SLACK_CHANNEL" --arg thread_ts "$2" '
    . + {channel: $channel, unfurl_links: false, unfurl_media: false}
    + (if $thread_ts == "" then {} else {thread_ts: $thread_ts} end)' <<< "$1" \
  | curl -sS -X POST "https://slack.com/api/chat.postMessage" \
    -H "Authorization: Bearer ${SLACK_BOT_TOKEN}" \
    -H "Content-Type: application/json; charset=utf-8" \
    --data-binary @-
}

response="$(post_message "$(jq -c '.[0]' "$MESSAGES")" "")"
if [[ "$(jq -r '.ok' <<< "$response")" != "true" ]]; then
  echo "::error::Slack chat.postMessage failed: $(jq -r '.error // "unknown"' <<< "$response")" >&2
  exit 1
fi
thread_ts="$(jq -r '.ts' <<< "$response")"

count="$(jq 'length' "$MESSAGES")"
for ((i = 1; i < count; i++)); do
  message="$(jq -c ".[$i]" "$MESSAGES")"
  response="$(post_message "$message" "$thread_ts")" || response='{"ok": false, "error": "request failed"}'
  if [[ "$(jq -r '.ok' <<< "$response" 2> /dev/null)" != "true" ]]; then
    echo "::warning::Slack thread reply \"$(jq -r '.text' <<< "$message")\" failed: $(jq -r '.error // "unknown"' <<< "$response" 2> /dev/null)" >&2
  fi
done
