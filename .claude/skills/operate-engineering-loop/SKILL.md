---
name: operate-engineering-loop
description: Drive a tracked Operate change in the orchestration cluster webapp (src/operate/) end to end — issue branch, implementation, gated validation, independent review, a draft PR, and repeated Copilot review resolution. Use when asked for the engineering loop, an end-to-end implementation loop, or branch -> implement -> review -> draft PR -> Copilot review. For a migration, frontend-operate-migrator drives this loop and adds the fidelity gate.
disable-model-invocation: true
---

# Operate Engineering Loop

Own a tracked Operate change from issue to a review-ready draft PR. Return to the engineer only
after the implementation, independent reviews, required PR checks, and Copilot review loop are
complete.

Scope is the Operate pod at `webapp/client/apps/orchestration-cluster-webapp/src/operate/`, plus
the route files in `src/routes/` and cross-pod primitives in `src/shared/` that the change requires.

Read [frontend-unit-test](../frontend-unit-test/SKILL.md) for browser-mode unit tests and
[frontend-integration-test](../frontend-integration-test/SKILL.md) for Playwright tests. For the
app's directory layout, pod areas and shared-code boundaries, see
`docs/monorepo-docs/frontend/orchestration-cluster-webapp.md`. Operate's own conventions — routing,
data fetching, state, styling, testing — are in
[operate-frontend](../operate-frontend/SKILL.md).

This skill stops at a review-ready **draft PR**. Send progress only at the end.

## Inputs

Required:

- A `camunda/camunda` issue number or URL.

Optional:

- A concrete implementation objective.
- Backport targets.
- Validation constraints.

If the issue does not define the implementation scope, ask once before proceeding. For bug fixes,
use provided backport targets or ask before opening the PR.

## Operating Rules

- Follow scoped instructions, module docs, and applicable skills.
- Track branch creation, implementation, validation, independent review, publication, and Copilot
  review in todos.
- Keep an internal convergence ledger for the acceptance matrix, review findings, Copilot threads,
  gate results, and Git/PR state. This is task state, not a repository artifact: never commit it.
- Never cache issue or PR state in a file. Query GitHub live every time it matters.
- Preserve unrelated worktree changes. Never implement or push on `main`.
- Rebase onto `origin/main`; never create a merge commit.
- Treat review findings as claims to verify. Fix valid findings at their root and rebut invalid
  findings with evidence.
- Never count an inaccessible or failed review as approval.
- Follow repository commit and PR conventions and keep the engineer as the sole commit author.
- Do not finish while a todo, review thread, unpushed commit, or requested review is outstanding.

## Validation loop

Run from `webapp/client`. Loop on the cheapest failing tier and graduate only when it is green.
`npm run lint` combines Prettier, ESLint, and Knip; `npm run prettier:format` fixes formatting.

| Tier | Required gates | When | Max |
|------|----------------|------|-----|
| **edit** | `npm run lint:prettier`; `npm run lint:eslint`; `npm run typecheck -w @camunda/orchestration-cluster-webapp` | After meaningful edits | 5 |
| **component** | Edit tier; `npm run test:unit -w @camunda/orchestration-cluster-webapp`; `npm run build -w @camunda/orchestration-cluster-webapp`; `npm run lint:knip` | When a component is complete | 5 |
| **PR** | `npm run test:integration -w @camunda/orchestration-cluster-webapp`; `npm run test:a11y -w @camunda/orchestration-cluster-webapp`; visual CI | After opening the draft PR | 3 |

Never regenerate visual snapshots locally. Stop at a tier's limit and report the exact blocker; an
iteration that repeats the same failure and fix without progress counts double.

## Phase 1: Prepare

Inspect GitHub authentication, issue hierarchy (including paginated subissues and `blockedBy`
prerequisites), linked work, branch, worktree, and recent `main` CI. Select an unblocked
implementation issue if given a tracker. Create `<issue-number>-<short-scope>` from `origin/main`,
then run the component tier on the untouched branch.

## Phase 2: Implement and Validate

Read the surrounding code and history, then build an acceptance matrix covering observable
behavior, API calls, URL state, loading/empty/error/forbidden states, permissions, tenancy, and
accessibility. Record intentional omissions. Make the smallest complete change, add behavior-level
coverage, and when practical prove the regression test fails without the fix. Run the edit tier
after meaningful edits and the component tier when a component is complete.

Do not suppress failures, weaken assertions without rationale, or update snapshots without
inspecting the result. Do not call a failure pre-existing without evidence.

## Phase 3: Independent Review

Once the component tier is green, run at least two independent review agents with different
perspectives:

1. A frontend domain specialist, reviewing the change against Operate's conventions in
   [operate-frontend](../operate-frontend/SKILL.md).
2. A separate high-confidence code reviewer, such as the `code-review` agent.

These are review perspectives, not skills under `.claude/skills/`. Select whichever independent
review agents the harness provides and give them these two briefs; do not invoke a nonexistent
skill.

Provide the issue, acceptance matrix, exact diff, rationale, and validation evidence. If a reviewer
cannot access the worktree, provide the patch or replace that review.

Verify every finding, address valid issues, strengthen coverage where needed, and rerun the cheapest
affected tier. Re-review the final diff until no actionable findings remain. Do not implement
speculative suggestions or expand the issue's scope. Each review has a maximum of 3 iterations; an
iteration repeating the same finding with no new evidence counts double.

## Phase 4: Publish the Draft PR

Immediately before committing:

```bash
git fetch origin main
git rebase --autostash origin/main
```

Rerun the edit and component tiers after rebasing, commit only in-scope files, and do not add AI
co-author trailers.

Push with an explicit refspec so a branch initially configured from `origin/main` cannot target
`main`:

```bash
git push -u origin HEAD:refs/heads/<branch-name>
```

Open a draft PR with `gh pr create --draft`, a conventional-commit title, and the completed
repository template. Assign the team reviewer and keep the PR draft after convergence — the engineer
decides when it is ready. Include:

```markdown
## Related issues

closes #<issue>
```

Use `relates to #<issue>` when the PR is one of several for that issue. A partial PR must not close
an issue whose remaining work is deferred.

Confirm the draft PR contains the latest pushed commit.

## Phase 5: Copilot Review Loop

Read and follow [references/copilot-review.md](references/copilot-review.md). Repeat until the latest
review recommends approval, adds no comments, and leaves no unresolved threads. Stop only for an
external blocker or repeated invalid feedback already answered with evidence.

## Phase 6: CI Convergence

Run the PR tier once the draft PR is open. Diagnose a failing check with `ci-fix-failure`; this
skill owns applying the valid fix, validating it locally, and pushing it. Rerun only verified
transient failures. After every push, refresh both CI and the unresolved review threads.

The PR tier's 3-iteration budget in the tiered validation loop covers this Copilot/CI
convergence — it is one budget, not an additional one. At the cap, report the exact blocker instead
of retrying blindly or claiming completion.

## Completion Gate

Before returning to the engineer, verify:

- the worktree is clean and the latest local commit is pushed to the draft PR
- all gates in all three tiers are green on the latest pushed SHA
- all independent review findings are addressed
- the latest Copilot review recommends approval
- no review threads or workflow todos remain unresolved
- the commit has the engineer as sole author, and the PR is correctly linked, still draft, and has
  the team reviewer assigned

Lead the final response with the draft PR number, then concisely state the delivered behavior,
number of review iterations, and any blocker. Routine green checks and test counts are implicit.

Every recurring failure mode becomes a rule, not a one-off fix: encode it in this skill so it
cannot recur.
