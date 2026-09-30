---
name: frontend-operate-migrator
description: Independently migrate an Operate page or component from the legacy client to the unified webapp, from an implementation issue through fidelity checks, reviewed draft PR, CI, and Copilot review.
disable-model-invocation: true
---

# Operate Frontend Migration

Own a ticket from `operate/client/` to
`webapp/client/apps/orchestration-cluster-webapp/src/operate/` through a review-ready draft PR.
Read [operate-frontend](../operate-frontend/SKILL.md) for both codebases' conventions,
[frontend-unit-test](../frontend-unit-test/SKILL.md) and
[frontend-integration-test](../frontend-integration-test/SKILL.md) for tests. This is a standalone
execution workflow.

## Authorization and ticket

An explicit invocation authorizes in-scope edits, commits, pushes, a draft PR, reviews, Copilot
review requests, and review-thread replies. It does not authorize merging, marking ready, other
repositories, CI/shared-action changes, issue closure, or tracker-body edits. Respect local-only,
no-push, no-comment, user-owned PR, and reviewer-type restrictions; ask if they block completion.
Do not assign human reviewers. For bug fixes, ask about backports before opening the PR.

The live inventory is [epic #32](https://github.com/camunda/experience-pdp/issues/32) -> page
bucket -> implementation leaf (no deeper nesting); broader deployment work is in
[camunda/camunda#51305](https://github.com/camunda/camunda/issues/51305). Start from an unblocked
implementation ticket. If given a tracker, inspect every level and select a leaf. Query each
child in its own repository; a private-epic 404 may require the existing keyring credential rather
than the active `GH_TOKEN`. `blockedBy` must reflect a required contract, not chronology or an
already-delivered/superseded prerequisite. A closed/not-planned child, merged PR, or stale body
is not proof that behavior shipped. Ask once if the surviving ticket's scope is unclear.

## Workflow

1. **Prepare.** Inspect current `main`, the legacy source, unified pages/routes, linked PRs,
   `blockedBy`, and worktree. One owner per issue/PR: reuse an existing PR branch; for new work use
   `<issue-number>-<short-scope>` from `origin/main`, or from a verified pushed parent SHA for a
   stack. Never create a duplicate PR, mutate another owner's branch, discard unrelated worktree
   changes, or implement/push on `main`. Keep one coherent behavior per PR under **1,500 added +
   deleted lines**, including tests/locales. Establish an untouched-branch component baseline.
2. **Prove fidelity.** Derive a matrix from **both** the ticket and legacy branches/effects:
   API shape, offset/cursor pagination, polling, URL and tenant context, permissions, navigation,
   initial/cached loading and errors, recovery, accessibility. Separate unchanged legacy behavior,
   approved improvements, and explicitly deferred preview work; preserve legacy UX by default.
   Map omissions to existing leaves before proposing new ones. Reuse shared auth, theme, notifications,
   viewers and editors; keep shareable state in validated URLs, server data in Query, and ephemeral
   state local. Only use a reducer/MobX when warranted.
3. **Implement and validate** from `webapp/client`. Test the assembled route for direct entry,
   pending/errors/retry, title, tenant variants and cross-layout CSS cleanup when applicable. Check
   that optional enrichment failures do not hide healthy primary data and failed filter resolution
   cannot broaden a query. Coordinate exclusive browser ports; release them after Playwright.

   | Tier | Commands | Max rounds |
   | --- | --- | --- |
   | Edit | `npm run lint:prettier`; `npm run lint:eslint`; `npm run typecheck -w @camunda/orchestration-cluster-webapp` | 5 |
   | Component | Edit tier; `npm run test:unit -w @camunda/orchestration-cluster-webapp`; `npm run build -w @camunda/orchestration-cluster-webapp`; `npm run lint:knip`; scoped locale gate below | 5 |
   | PR | `npm run test:integration -w @camunda/orchestration-cluster-webapp`; `npm run test:a11y -w @camunda/orchestration-cluster-webapp`; visual CI | 3 |

   Run the cheapest affected checks first, then the full tier before publication. Never suppress
   warnings, weaken tests, or regenerate visual snapshots to hide a regression. From the repo root,
   run `node .claude/skills/frontend-operate-migrator/scripts/fidelity.mjs --ported <component-dir>`
   after the edit tier; all `operate.*` keys must exist in en/de/fr/es. Omit this scoped gate from
   the untouched-branch baseline if the component does not exist yet.
4. **Review locally before pushing.** Get independent Operate/frontend and high-confidence code
   reviews, plus a read-only **legacy -> migrated** branch/effect review (including shared logic not
   copied per consumer). These are briefs, not prescribed agent types. Provide the ticket, matrix,
   exact sources/diff and checks. Verify findings, fix valid ones, rerun affected checks and reviews;
   max 3 rounds per perspective. An IDE review, if available, helps but cannot guarantee GitHub
   Copilot's later verdict. If the user demands approval/green PR CI before pushing, explain that
   both require a published head and agree on a publication rule first.
5. **Publish.** Fetch and rebase onto the verified base (normally `origin/main`; exact pushed
   parent for a stack), never merge or rewrite an upstream owner's branch. Rerun affected checks
   after code-changing rebases. Commit only the ticket with the engineer as sole author and no AI
   co-author trailer; use `feat: migrate Operate <PageName> page to unified app` when applicable.
   Protect unrelated changes before rebasing. Push via
   `git push -u origin HEAD:refs/heads/<branch-name>`. For new work,
   open a **draft** PR with a conventional-commit title and `.github/pull_request_template.md`:
   brief `## Description`, applicable `## Checklist`, and `## Related issues` (`closes` only if the
   implementation issue is complete). Note intentional differences and genuine blockers briefly.
   For an existing PR, preserve its branch/state; verify its published head and base.
6. **Converge on the current head.** Request Copilot PR review through GraphQL `requestReviews`
   with the bot node ID and `union: true` (REST reviewer requests can silently fail). Record prior
   review IDs, check every 30 seconds for up to 10 minutes for a *new* review, inspect inline
   findings, reply within each thread, and resolve only after the reply succeeds. Do not substitute
   `gh pr edit --add-reviewer`; it can fail on deprecated Projects Classic fields. If comments are
   forbidden, stop and report
   the unresolved-thread blocker. Refresh CI and Copilot on every new pushed SHA; old-head approval
   is not approval. Diagnose failures with `ci-fix-failure`, inspect visual diffs, rerun verified
   transient failures only. Never bypass a fail-closed gate or start off-scope remediation. The
   PR tier's 3 rounds cover both CI and Copilot.

## Done

Return only when the clean worktree's latest sole-author commit is pushed, all required gates are
green on that SHA, reviews have no actionable findings, the latest Copilot review recommends
approval, and no thread/todo remains. Otherwise give the exact blocker. Lead with the draft PR
number and delivered behavior. Issue/page/epic closure is separate post-merge work: verify the
latest `main` and acceptance first; do not rewrite tracker bodies without authorization.
