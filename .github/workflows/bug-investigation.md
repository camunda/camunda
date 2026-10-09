---
emoji: 🔍
description: Runs the bug-investigation skill against an issue when the `investigate` label is added.
on:
  label_command:
    name: investigate
    events: [issues]
engine: claude
timeout-minutes: 90
permissions:
  contents: read
  issues: read
  pull-requests: read
network:
  allowed:
    - defaults
    - github
    - github-actions
    - java
    - node
tools:
  github:
    mode: gh-proxy
    toolsets: [default]
  comment-memory:
    memory-id: investigation
safe-outputs:
  create-pull-request:
    draft: true
    excluded-files:
      - ".github/workflows/**"
      - ".claude/**"
      - "**/*.lock"
---

# Bug Investigation

Read `.claude/skills/bug-investigation/SKILL.md` in full and execute it exactly, for issue
`#${{ github.event.issue.number }}` in `${{ github.repository }}`.

Follow the skill's phases and hard rules as written, with these adaptations for running
headlessly in this workflow instead of an interactive session:

- You are running autonomously — no engineer is present to answer questions. Per Hard rule 9,
  skip Phase 0's "post progress, or just report back?" question entirely: always post, never ask.
- Write your running progress report to the managed `investigation` comment-memory file instead
  of posting/PATCHing a GitHub comment directly — the framework syncs it to a single comment on
  the issue for you.
- Do not call the GitHub API directly to open a pull request. When the skill's confidence gate
  (Medium or High) is met, describe the fix as a normal set of file edits in this checkout; the
  `create-pull-request` safe output opens the draft PR from your changes once you finish.
- If the skill would otherwise post a second comment or edit an existing one directly, fold that
  content into the same comment-memory file instead — only one managed comment should exist for
  this investigation.
- Log completion with `noop` and a short explanation if the issue turns out not to be
  reproducible or in scope for this repository.
