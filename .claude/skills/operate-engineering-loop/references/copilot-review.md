# Copilot Review Loop

Record existing Copilot review IDs before requesting a new review. Use GraphQL: the REST reviewer
endpoint accepts the request but silently ignores Copilot.

```bash
pr_node=$(gh api repos/camunda/camunda/pulls/<pr> --jq .node_id)
bot_node=$(gh api users/copilot-pull-request-reviewer%5Bbot%5D --jq .node_id)

gh api graphql -f query="
mutation {
  requestReviews(input: {pullRequestId: \"$pr_node\", botIds: [\"$bot_node\"], union: true}) {
    pullRequest { id }
  }
}"
```

Keep `union: true`; omitting it silently drops existing human reviewers.
Do not fall back to `gh pr edit --add-reviewer`; it can fail on deprecated Projects Classic fields.

Copilot reviews appear asynchronously in `reviews`, not as persistent review requests. Poll every
30 seconds for up to 10 minutes. If no new review appears, stop and report the timeout. Inspect the
new summary and inline comments:

```bash
gh api repos/camunda/camunda/pulls/<pr>/reviews
gh api repos/camunda/camunda/pulls/<pr>/comments
```

For every new comment:

1. Classify it as valid, invalid, already handled, or out of scope.
2. Fix valid findings, validate and publish through the
   [engineering loop](../SKILL.md#validation-loop), without amending.
3. Reply with the fix or concrete evidence that the finding is invalid.
4. Resolve the thread through `resolveReviewThread`.

After every feedback push, refresh unresolved threads and request another Copilot review.
