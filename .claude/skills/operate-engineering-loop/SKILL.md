---
name: operate-engineering-loop
description: Independently deliver a tracked Operate change in the unified webapp from issue to reviewed draft PR. Use for the end-to-end implementation, validation, local review, CI, and Copilot review loop.
disable-model-invocation: true
---

# Operate Engineering Loop

Own a `camunda/camunda` issue through a review-ready draft PR in
`webapp/client/apps/orchestration-cluster-webapp/src/operate/`. Read
[operate-frontend](../operate-frontend/SKILL.md) for code conventions,
[frontend-unit-test](../frontend-unit-test/SKILL.md) and
[frontend-integration-test](../frontend-integration-test/SKILL.md) for browser tests.

## Authorization and scope

An explicit invocation authorizes in-scope edits, commits, pushes, a draft PR, independent reviews,
Copilot review requests, and review-thread replies. It does not authorize merging, marking ready,
other repositories, CI/shared-action changes, issue closure, or tracker-body edits. Honor a user's
local-only, no-push, no-comment, or user-owned PR boundary; if it prevents completion, ask. Do not
assign human reviewers. For bugs, ask about backports before opening the PR.

## Workflow

1. **Prepare.** Read the live issue, hierarchy/`blockedBy`, linked PRs, worktree, and recent `main`
   CI. A private-issue 404 may be the active `GH_TOKEN`: try the existing keyring credential without
   printing tokens. If scope is unclear, ask once. Reuse an existing PR's worktree and branch; for
   new work, create `<issue-number>-<short-scope>` from `origin/main`. For a stack, verify the
   exact pushed parent SHA and PR base. One owner per issue/PR; never create a duplicate branch or
   mutate another owner's branch. Preserve unrelated worktree changes and never implement or push
   on `main`. Establish an untouched-branch component baseline.
2. **Implement.** Build an acceptance matrix from the issue and surrounding code: behavior, API,
   URL/tenant state, permissions, pagination, polling, initial/cached loading and errors, recovery,
   and accessibility. Test optional lookup failure while primary data succeeds where applicable.
   Test real-route pending/error/title and cross-layout CSS behavior when touching routes or styles.
   Record intentional omissions; fix the smallest complete scope, not unrelated code.
3. **Validate** from `webapp/client`. Retry only with new evidence; do not weaken tests, suppress
   warnings, or regenerate visual snapshots to hide a failure.

   | Tier | Commands | Max rounds |
   | --- | --- | --- |
   | Edit | `npm run lint:prettier`; `npm run lint:eslint`; `npm run typecheck -w @camunda/orchestration-cluster-webapp` | 5 |
   | Component | Edit tier; `npm run test:unit -w @camunda/orchestration-cluster-webapp`; `npm run build -w @camunda/orchestration-cluster-webapp`; `npm run lint:knip` | 5 |
   | PR | `npm run test:integration -w @camunda/orchestration-cluster-webapp`; `npm run test:a11y -w @camunda/orchestration-cluster-webapp`; visual CI | 3 |

   Run the cheapest affected checks first, then the full tier before publication. Coordinate and
   release exclusive browser preview ports when other sessions run Playwright.
4. **Review locally before pushing.** Use two independent perspectives: Operate frontend fidelity
   and high-confidence code correctness. Give reviewers the issue, acceptance matrix, exact diff,
   and check results; respect the user's agent-type restrictions. Verify findings, fix valid ones,
   and rerun affected checks/reviews; max 3 rounds per perspective. Genuine IDE review, if
   available, adds evidence but **cannot guarantee** GitHub Copilot approval. If the user demands
   green PR CI or Copilot approval before any push, explain that these require a published head and
   agree on a publication rule first.
5. **Publish.** Fetch and rebase onto the verified PR base (normally `origin/main`, never merge);
   protect unrelated worktree changes before rebasing; never discard them.
   rerun affected checks after code-changing rebases. Do not rewrite a reviewed stack just for an
   empty upstream commit. Commit only in-scope files with the engineer as sole author, no AI
   co-author trailer. Push using `git push -u origin HEAD:refs/heads/<branch-name>`. For new work,
   open a **draft** PR using `.github/pull_request_template.md`, a conventional-commit title,
   concise description, and `## Related issues` (`closes` only for a complete issue). For an
   existing PR, update it rather than opening another. Verify the published head and base.
6. **Converge on the current head.** Follow [Copilot review procedure](references/copilot-review.md):
   request through GraphQL with `union: true`, inspect the new review and inline threads, verify
   findings, reply under each thread and resolve only after the reply succeeds. Do not comment if
   forbidden by the user; report the resulting blocker. Refresh CI and Copilot review **after every
   push**; old-head approval is not approval. Diagnose failures with `ci-fix-failure`, inspect visual
   diffs, rerun only verified transient failures, and never bypass a fail-closed security gate or
   launch other-repository remediation without authorization. The PR tier's 3 rounds cover both
   CI and Copilot, not three rounds each.

## Done

Return only when the clean worktree's latest sole-author commit is pushed, all required gates are
green on that SHA, independent findings are addressed, the latest Copilot review recommends
approval with no pending review, and no threads/todos remain. Otherwise state the exact blocker,
not a success claim. Lead with the draft PR number and delivered behavior. Post-merge issue
reconciliation is a separate, explicitly authorized task; a merged PR or closed child count alone
does not prove page completion.
