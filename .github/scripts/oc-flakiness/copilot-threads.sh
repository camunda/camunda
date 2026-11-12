#!/usr/bin/env bash
# Print the Copilot review threads of a PR that are eligible to be answered,
# as a JSON array on stdout. Empty array on any failure.
#
#   REPO=owner/name PR=123 [REVIEW_IDS='["1","2"]'] copilot-threads.sh
#
# Eligible means: unresolved, opened by Copilot, and carrying no comment from
# anyone else. Both callers need exactly this set and must agree on it -- the
# responder to decide what the agent is shown, the publisher to decide what it
# is allowed to reply to and resolve -- and the publisher must derive it
# itself, from the PR, rather than trust a file that passed through the
# responder's runner.
#
# REVIEW_IDS, when a non-empty JSON array, narrows the result to threads opened
# by those reviews. An empty array means every unresolved Copilot thread is in
# scope: answering one twice is a far smaller failure than skipping the round.
set -uo pipefail

: "${REPO:?REPO is required}"
: "${PR:?PR is required}"
COPILOT="${COPILOT:-copilot-pull-request-reviewer}"
review_ids="${REVIEW_IDS:-[]}"
printf '%s' "$review_ids" | jq -e 'type == "array"' >/dev/null 2>&1 || review_ids='[]'

raw=$(mktemp)
trap 'rm -f "$raw"' EXIT

# Paginated and slurped into one flat array: a single first:100 page could push
# this round's findings off the end behind resolved ones and leave the count at
# a false zero.
# shellcheck disable=SC2016 # $owner/$repo/$pr/$endCursor are GraphQL variables, bound by -F
gh api graphql --paginate -f query='
  query($owner:String!,$repo:String!,$pr:Int!,$endCursor:String){
    repository(owner:$owner,name:$repo){
      pullRequest(number:$pr){
        reviewThreads(first:100, after:$endCursor){
          pageInfo{hasNextPage endCursor}
          nodes{
            id isResolved isOutdated
            comments(first:20){nodes{
              author{login} path line body url
              pullRequestReview{fullDatabaseId}}}}}}}}' \
  -F owner="${REPO%/*}" -F repo="${REPO#*/}" -F pr="$PR" 2>/dev/null \
  | jq -s '[.[].data.repository.pullRequest.reviewThreads.nodes[]?]' > "$raw" 2>/dev/null \
  || echo '[]' > "$raw"

# Only Copilot-authored bodies are forwarded even from the threads that
# survive. Everything here is interpolated into the prompt of an agent that
# runs with --dangerously-skip-permissions, and anyone can reply inside a
# Copilot thread on a public repository -- so an unfiltered reply is an
# instruction channel into that agent. Dropping a touched thread whole also
# keeps one this round deliberately left open out of any later round, with the
# human's question intact.
jq --arg bot "$COPILOT" --argjson ids "$review_ids" '
  def is_copilot: . == $bot or . == $bot + "[bot]";
  [
  .[]?
  | select(.isResolved == false)
  | select(((.comments.nodes[0].author.login) // "") | is_copilot)
  | select([.comments.nodes[] | (.author.login // "") | is_copilot] | all)
  | ((.comments.nodes[0].pullRequestReview.fullDatabaseId // "") | tostring) as $rid
  | select(($ids | length) == 0 or ($ids | index($rid)) != null)
  | {id,
     path: .comments.nodes[0].path,
     line: .comments.nodes[0].line,
     url: .comments.nodes[0].url,
     outdated: .isOutdated,
     comments: [.comments.nodes[]
                | select((.author.login // "") | is_copilot)
                | {author: (.author.login // "?"), body}]}
]' "$raw" 2>/dev/null || echo '[]'
