---
name: babysit-pr
description: Shepherd one or more camunda/camunda PRs through flaky CI and the merge queue to merged. Accepts a list of PRs (e.g. a merge plus its backports). Use when asked to babysit, shepherd, watch, drive, or "get merged" one or more PRs, or to keep retrying CI until they land.
---

# Babysit PR(s) to merge

Drive PR(s) open → merged. Handle **transient failures only**: rerun flaky CI, enqueue when green +
approved, re-enqueue after flaky merge-queue removal. Real failure → hand back to human.

**Starts after review.** Reviews are a human gate — never wait on them. Normal PR must be
**approved** before entering the loop; unapproved → report not-ready, stop. Backports exempt (bot
approves them, step 2).

Owns its own loop. Invoke once; tends PR(s) across turns until all merged. No `/loop` wrapper.

Acts (reruns, enqueues) — unlike `ci-fix-failure`, which only diagnoses. Never edits code, addresses
comments, masks a failure, or lowers the bar to force a merge.

## Inputs

PR numbers in `camunda/camunda` or URLs. Shapes:

- Single: `babysit #12345`.
- **Merge + backports**: original + `backport stable/X.Y` PRs from the bot. Pass the set, or pass
  the original and discover backports via `gh pr list --repo camunda/camunda --search "<orig> in:body is:open"`
  or the bot's linked-PR comment.

No PR → ask; don't infer from context.
Non-`camunda/camunda` → ask human for confirmation, to assume monorepo conventions (GitHub Merge Queue, backport-action).

## Prerequisites

- `gh auth status` works against `camunda/camunda`; CWD = repo root.

## Hard rules

1. **Rerun/re-enqueue = unblock, not fix.** Transients toward `main`/`stable` aren't acceptable
   long-term; real fix is hardening (retries-in-timeout, caching, runner sizing). Note every
   rerun; a check that flakes repeatedly → stop retrying, surface as a flake to fix.
2. **Never edit code/config/workflows to pass CI.** Code fix = author's call → report, stop that PR.
3. **Never bump a timeout, disable a test, or bypass the queue.** Timeouts are contracts; bypass is
   incidents-only.
4. **A passing rerun does NOT clear a Flaky Test Gate alert** — sticky
   (`docs/monorepo-docs/flaky-test-gate.md`). Needs a code change or `ci:flaky-test-bypass` label —
   author's call.
5. **Cancelled = real until proven otherwise.** Fetch annotations first; usually GHA timeouts.
6. **Cap the loop.** Rerun any check ≤ **3×**; re-enqueue after flaky removal ≤ **3×**. `main`/
   `stable` paths: escalate sooner.

## Flaky (rerun) vs real (stop)

Classify before every rerun. Unsure → **real**, stop.

**Rerun (transient):** known flake ([dashboard](https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo));
infra noise (network, registry, runner provisioning); failure unrelated to diff AND green on
target; merge-queue failure on the *temp* branch, not PR head.

**Stop (real):** compile/assertion/lint/spotless/license failure in touched code; same check fails
again after rerun; `BEHIND`/`CONFLICTING` (author rebase); merge-queue removal from a concurrently
merged dependency ([ci.md §6](../../../docs/monorepo-docs/ci.md#why-is-my-ci-check-failing)); Flaky Test Gate alert.

Non-obvious failure → delegate diagnosis to `ci-fix-failure`, act on its verdict. Don't reimplement
log-reading.

## Loop

One row per PR. Repeat passes until every PR terminal, then report and stop.

**Terminal:** `MERGED` ✅ · not-approved normal PR (needs review) 🕓 · needs-author ⛔ (real
failure, conflict, `BEHIND`, Flaky Test Gate) · cap hit 🔁✋.

### Each pass (per non-terminal PR)

**1. State**

```bash
gh pr view <pr> --repo camunda/camunda \
  --json number,title,state,isDraft,mergeable,mergeStateStatus,reviewDecision,autoMergeRequest,headRefName,baseRefName,statusCheckRollup
```

- `MERGED` → ✅.
- `isDraft` → bot's conflict-backport (committed conflict markers) or not-ready PR. Don't resolve
  unless asked → ⛔.
- `CONFLICTING` / `BEHIND` → author rebase (don't rebase their branch unless asked) → ⛔.

**2. Approval gate** (`reviewDecision`) — check before any CI work

- Normal PR, not `APPROVED` → **not ready to babysit.** Report 🕓 needs-review and mark terminal.
  Do NOT rerun CI or loop waiting for review — reviews are a human gate.
- Backport → bot auto-approves + auto-merges once green; no human gate. Proceed.
- Approved → proceed.

**3. Checks** (`statusCheckRollup` / `gh pr checks <pr>`)

- Running → skip this pass.
- Failed → classify. Transient & under cap → rerun failed jobs only:
  ```bash
  gh run rerun <run-id> --failed --repo camunda/camunda
  ```
  Log "rerun N/3 of <check>". Real or cap hit → ⛔ + link.

**4. Enqueue** (normal PR, green + approved)

```bash
# = "Merge when ready": adds to the branch's merge queue, which applies its own
# configured merge method. Author squashes commits before enqueueing (CONTRIBUTING.md).
gh pr merge <pr> --repo camunda/camunda --auto
```

`autoMergeRequest` non-null = queued. Backports self-merge via the bot — don't enqueue one unless
automation clearly stalled and the engineer asks.

**5. Merge queue** — re-enqueue on flaky removal

Queue builds temp branch (target + PR) and reruns CI. On removal, inspect:

```bash
gh run list --repo camunda/camunda --event merge_group --branch <baseRefName> --limit 10
```

Flaky on temp branch & under cap → re-enqueue (repeat step 4) — sanctioned retry
([CONTRIBUTING.md](../../../CONTRIBUTING.md)). Real → ⛔.

### Between passes

Don't busy-wait. When every non-terminal PR is just waiting on CI, block until CI moves, then next
pass. Background poll → re-invoked on change:

```bash
while :; do
  for pr in <pr-list>; do
    gh pr checks "$pr" --repo camunda/camunda 2>/dev/null | grep -q pending || exit 0
  done
  sleep 300
done
```

Floor of a few minutes. Stop the loop once all PRs terminal.

## Reporting

Live status line per PR:

```
#12345 (main)        📥 enqueued
#12346 (stable/8.7)  🔁 reran unit-tests (flake 1/3)
#12347 (stable/8.6)  ✅ merged
```

Final summary: what merged, what needs the engineer, any check that flaked repeatedly (hardening
candidate, rule 1). Link runs by URL; code refs use the repo's stable-permalink convention.
