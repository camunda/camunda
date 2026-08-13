# AGENTS.md

## Pull request conventions

- PR title should be clear and descriptive, and follow Conventional Commits with no scope (the
  release-notes PR-gate lints it).
- Link the tracked issue under a `## Related issues` section (this exact heading — the release-notes
  gate only reads refs inside it; a ref elsewhere in the body does not count). Link the issue, NOT a PR:
  - `closes #1234` (or `fixes`/`resolves`) when this PR fully resolves the issue — auto-closes it on merge.
  - `relates to #1234` or a bare `#1234` when this is one of several PRs for the issue (an epic, or work
    split across releases) — satisfies the gate but does NOT close the issue.
  - No tracked issue (hotfix, dep bump, CI/refactor)? Tick `- [ ] This PR does not need a linked issue`.
    When opening the PR yourself (CLI/API), reproduce this section — GitHub only auto-fills the template in
    the web UI, so an agent-authored body must include it explicitly.

