---
emoji: 🧭
name: Route to owning team
description: Routes an issue to the Camunda team that owns it, using the ownership-routing runbook in camunda/eng-kit-memory. Triggered by the `c8-route` label or a `/c8-route` comment.
on:
  label_command:
    name: c8-route
    events: [issues]
  slash_command:
    name: c8-route
    events: [issues, issue_comment]
engine: claude
timeout-minutes: 15
permissions:
  contents: read
  issues: read
  pull-requests: read
network:
  allowed:
    - defaults
    - github
tools:
  github:
    mode: gh-proxy
    toolsets: [default]
steps:
  # The ownership-routing runbook and the team entries live in the private
  # camunda/eng-kit-memory. Both steps are continue-on-error on purpose: a
  # missing runbook degrades the routing to .codeowners only, which the agent
  # must then declare in its Gaps section. It must never fail the run silently.
  - name: Generate eng-kit-memory read token
    id: memory-token
    continue-on-error: true
    uses: camunda/infra-global-github-actions/generate-github-app-token-from-vault-secrets@main
    with:
      github-app-id-vault-key: GITHUB_APP_ID
      github-app-id-vault-path: secret/data/products/qa/ci/github.com/apps/camunda/qa-processes
      github-app-private-key-vault-key: GITHUB_APP_SECRET
      github-app-private-key-vault-path: secret/data/products/qa/ci/github.com/apps/camunda/qa-processes
      vault-auth-method: approle
      vault-auth-role-id: ${{ secrets.VAULT_ROLE_ID }}
      vault-auth-secret-id: ${{ secrets.VAULT_SECRET_ID }}
      vault-url: ${{ secrets.VAULT_ADDR }}
      owner: camunda
      repositories: eng-kit-memory
      read-only: true

  - name: Check out the ownership-routing runbook
    id: memory
    if: steps.memory-token.outcome == 'success'
    continue-on-error: true
    uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
    with:
      repository: camunda/eng-kit-memory
      token: ${{ steps.memory-token.outputs.token }}
      path: eng-kit-memory
      persist-credentials: false
      sparse-checkout: |
        skills
        memory/teams

  - name: Record the routing runbook version
    id: routing
    env:
      MEMORY_OUTCOME: ${{ steps.memory.outcome }}
    run: |
      set -euo pipefail
      if [ "$MEMORY_OUTCOME" = "success" ]; then
        echo "sha=$(git -C eng-kit-memory rev-parse HEAD)" >> "$GITHUB_OUTPUT"
      else
        echo "::warning::camunda/eng-kit-memory could not be read; routing will fall back to .codeowners and must say so"
        echo "sha=" >> "$GITHUB_OUTPUT"
      fi
safe-outputs:
  add-comment:
    max: 1
---

# Route to owning team

Answer one question about issue `#${{ github.event.issue.number }}` in
`${{ github.repository }}`: **which Camunda team owns this problem, and how is it
reached.** Then stop.

The request that triggered you:

```
${{ steps.sanitized.outputs.text }}
```

## Follow the runbook

Read `eng-kit-memory/skills/route.md` in full and execute it exactly, together
with `eng-kit-memory/skills/reference/ownership-signals.md`, which is the
catalog of signals that runbook selects from. Teams are discovered by listing
`eng-kit-memory/memory/teams/` — never by a filename glob, and never from an
index.

Adaptations for running headlessly here, instead of in an interactive session:

- No engineer is present to answer a clarifying question. If the issue is
  genuinely ambiguous between two teams, say so, name both, and explain what
  would disambiguate — do not pick one to appear decisive.
- If the checkout of `eng-kit-memory/` is missing, the runbook and all team
  entries are unavailable. Do **not** guess a team from the issue text. Say the
  routing could not be performed, name what was missing, and stop.
- You have read-only access and no ability to change code or open pull
  requests. If the issue asks for a fix as well as a routing, route it and say
  the fix is out of scope for this workflow.

## Output

Post exactly one comment, in this shape:

```markdown
**Routing:** <Team Name> · #ask-<slack_channel>

<one paragraph of reasoning, quoting the reporter's own words where they
decided it>

**Sources**
- <signal> — <what you read to reach this>

**Gaps**
- <signal> — <what you tried, the error, and what it cost this routing>

<sub>Wrong team? Comment `/c8-route` and say which team it should be.</sub>
```

Name the team in plain text with its Slack channel. Do not write an
`@camunda/<team>` mention: agent-authored mentions are escaped before
publication, so it would render as literal text rather than notifying anyone.

Omit the **Gaps** section only when every signal you selected was available.

If this is not an ownership question at all — a feature request, a
support question, a discussion — post a single line saying so and stop. No
team, no fallback, no guess.

## Hard rules

- **Never guess a team.** A Gaps line beside a wrong team is recoverable; a
  confident wrong team is not. It sends someone to the wrong channel and they
  may not come back.
- **Report every signal you could not read**, with what you tried, the error,
  and the cost in routing terms — "the eng-kit-memory checkout failed, so team
  entries are dark and this routing used `.codeowners` alone, which cannot
  distinguish the teams that share a directory". Never write "unavailable" and
  move on.
- **Route to current ownership.** A pre-reorg name resolves through a team's
  `aliases[]`. When several successors claim the same alias, that is a reason
  to ask a disambiguating question, not to pick.
- **Component labels are not teams.** Zeebe, Operate, Tasklist, Identity and
  Optimize each span several teams. Disambiguate by symptom.
- **Answer no technical questions.** An answerable question still gets a
  routing, not an answer.
- **Never paste customer names, cluster IDs, Slack user IDs, or tokens** into
  the comment. Cite an incident by ID only.
