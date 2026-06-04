# Flaky Test Gate (sticky alerts)

The flaky test gate is a CI quality gate in the [Unified CI](./ci.md#unified-ci) that blocks PRs from introducing **new** flaky tests. Once a test is flagged on a PR, the alert is **sticky** — it remains until either the test is actually fixed or the bypass label is applied. A re-run that happens to pass does **not** silence the alert.

This page is the canonical reference for how the gate behaves: it covers the rules, the comment UX, the workflow integration, and the operational concerns. The implementation lives in [.github/actions/detect-new-flaky-tests/](https://github.com/camunda/camunda/tree/main/.github/actions/detect-new-flaky-tests) and is wired into [.github/workflows/ci.yml](https://github.com/camunda/camunda/blob/main/.github/workflows/ci.yml) as the `detect-new-flaky-tests` job.

## Why sticky?

Before sticky alerts, the bot resolved the comment whenever a subsequent CI run found no new flakes. That meant **any retry-luck pass silenced the alert** — authors could hit "Re-run jobs" until CI went green and merge with the latent flake still present. Empirical audit of the 6-day window after PR [#53683](https://github.com/camunda/camunda/pull/53683) showed that 4 out of 4 "resolved" alerts in that window had **no code fix** — they were all pure retry luck.

The sticky design closes that loophole. The trade-off is friction: real flaky-test fixes now need to demonstrate that they hold across multiple runs before the gate releases the PR.

## Clearance rules

An alert entry stays `active` until one of these conditions is met:

| Path | Trigger | Resulting status |
|------|---------|------------------|
| **Method fix** | The flagged test's method body is modified, AND the originally affected job runs clean (no Maven retries) for at least **3** subsequent gate runs. | `cleared_via_fix` |
| **Bypass label** | The `ci:flaky-test-bypass` label is applied to the PR. | `cleared_via_bypass` |
| **Force-push drop** | The PR is rewritten so the original flagging commit is no longer reachable, AND the test does not flake in the new history. | `dropped_force_push` |

"Clean run" has a strict definition (Def-2):
- The test was actually observed in this run (the job wasn't skipped/cancelled), AND
- The test did not appear in `FLAKY.xml` (passed first try, zero Maven retries).

A Maven retry-pass (test failed once and passed on retry) still ends up in `FLAKY.xml`, which **resets** the counter to 0. The gate considers any retry as evidence the fix did not hold.

The counter is per-test and scoped to the **same parent job** as the original flake. Matrix entries roll up to the parent name — e.g. `identity-tests/identity-tests - elasticsearch9` rolls up to `identity-tests`. A clean run in a different job does not advance the counter.

Re-runs of the same SHA count just like runs on later commits. Authors don't have to push throwaway commits to bank evidence — but the gate is only reachable after a real modification, so re-run-spam alone does not advance the counter.

## What developers see

The gate maintains a single PR comment, identified by the hidden marker `<!-- new-flaky-tests-alert -->`. Its content reflects the current state.

### Active alert

```
# ⚠️ New Flaky Tests Detected

This PR introduces **1 new flaky test(s)** that are not currently
flaky on `main`, `stable/*`, or in any other open PR.

- **shouldFlushBatchWhenFull**
  - Jobs: `general-unit-tests`
  - Package: `io.camunda.exporter.appint.subscription`
  - Class: `SubscriptionTest`
  - State:
    - First flagged at: `abc1234`
    - Method last modified at: — (no fix detected)
    - Clean re-runs since fix: 0 / 3
    - Last observed: 2026-06-03 09:42:00 UTC

---

**What to do:**
1. Fix the flaky test method, push the commit, then let CI run 3 times.
2. If unrelated to your changes: add the `ci:flaky-test-bypass` label.
```

### Active alert after a fix attempt

```
  - State:
    - First flagged at: `abc1234`
    - Method last modified at: `def5678`
    - Clean re-runs since fix: 2 / 3
    - Last observed: 2026-06-03 10:21:00 UTC
```

The counter advances each time CI runs and the test passes cleanly in the originally affected job.

### Mixed: some active, some cleared

```
# ⚠️ New Flaky Tests Detected
...
- **stillBroken** ...

<details>
<summary>1 cleared test(s) (history)</summary>

- ~~**alreadyFixed**~~
  - ~~Class: `SomeIT`~~
  - ~~cleared on `def5678` after 3 clean re-runs~~

</details>
```

### All clear

```
# ✅ Cleared — No outstanding new flakes

All previously flagged tests cleared via fix + 3 clean re-runs, or via
`ci:flaky-test-bypass` label.

<details>
<summary>Previous warning</summary>
... strikethrough history ...
</details>
```

The "Cleared" headline is intentionally distinct from the legacy "Resolved" word — the old "Resolved" template fired on any retry-luck pass, and reusing it would conflate the two behaviors.

## When the gate runs

The job runs on `pull_request` events from non-fork repos, with these exclusions:

- PRs from `monorepo-devops-automation[bot]` (backport bot).
- PRs from `renovate[bot]` — dependency bumps don't introduce flakes; any flake they hit is pre-existing infra noise. See [#53683](https://github.com/camunda/camunda/pull/53683) for the rationale.
- PRs whose `head_ref` starts with `backport` (manually-named backports).

Direct fixes pushed to `stable/*` branches by humans still run the gate.

## Common scenarios

### A. PR introduces a genuine new flake

1. CI runs; test flakes; bot posts active alert. `clean_runs_since_modified: 0 / 3`.
2. Author edits the test method (or the production code it exercises), pushes commit.
3. Gate detects the modification via `git log -L`; `Method last modified at` updates.
4. Next CI runs; test passes cleanly; counter advances `0 → 1 → 2 → 3`.
5. At counter 3, status flips to `cleared_via_fix`; comment becomes "✅ Cleared".

### B. PR triggers an unrelated flake

1. CI runs; test flakes; bot posts active alert.
2. Author concludes the flake is pre-existing infra noise.
3. Author opens a `kind/flake` issue documenting the test, job, and run.
4. Author applies the `ci:flaky-test-bypass` label.
5. Next CI runs; all active entries → `cleared_via_bypass`; comment becomes "✅ Cleared".

### C. Author tries to merge by re-running CI repeatedly

1. CI runs; test flakes; bot posts active alert. `Method last modified at: —`.
2. Author hits "Re-run jobs" three times without touching the code.
3. Each run sees the test pass on retry. Counter **stays at 0** — there's no modification, so the counter doesn't increment regardless of how many clean runs accumulate.
4. Alert remains active; merge stays blocked.
5. Author has to either actually fix the test or apply the bypass label.

### D. Author force-pushes to rewrite history

1. CI runs on commit X; test flakes; entry stored with `first_flagged_sha: X`.
2. Author rebases the branch; new HEAD is Y; commit X is no longer reachable.
3. Next CI run: gate re-evaluates against the new history.
   - If the offending code is still present and the test still flakes → entry stays active; counter logic continues with a new anchor SHA via `git log -L` against `merge-base(base_ref, HEAD)..HEAD`.
   - If the offending code was dropped from the new history AND the test isn't flaking → entry → `dropped_force_push`; alert clears.
4. There's no way to force-push to silence an active flake — the gate always re-evaluates and re-detects the modification (or its absence).

## Workflow integration

The `detect-new-flaky-tests` job is defined in `.github/workflows/ci.yml`. The job stays thin: it computes the run-specific context (bypass label, ran-jobs JSON from `needs.*.result`, BigQuery baseline) and hands it to the `detect-new-flaky-tests` composite action, which owns all sticky-state plumbing. Key implementation details:

- **Checkout** uses `fetch-depth: 0` because `git log -L` requires full history.
- **State artifact** is named `flaky-gate-state-pr-<PR_NUMBER>` with 30-day retention. The action downloads the latest non-expired artifact via `gh api`, mutates the JSON, and re-uploads with `overwrite: true` — the workflow no longer wires state files in/out.
- **No-op short-circuit** lives in the detector: when there is no prior state, no new flakes this run, and no bypass label, it returns without posting a comment or writing state, so clean PRs are left untouched.
- **Hidden comment marker** `<!-- flaky-gate-state-artifact: <name> -->` points to the artifact for cross-reference.
- **BigQuery baseline** is queried only when this run produced new flakes. The query lives in the workflow step "Query known flaky tests from BigQuery."
- **Blocking** is `true` (default) — sticky entries fail `check-results`.

### State schema

```json
{
  "schema_version": 1,
  "pr_number": 54375,
  "last_known_head_sha": "ad7705f...",
  "last_updated_at": "2026-06-03T11:42:00Z",
  "tests": [
    {
      "key": "io.camunda.it.auth.SomeIT.shouldDoX",
      "package": "io.camunda.it.auth",
      "class_name": "SomeIT",
      "method_name": "shouldDoX",
      "file_path": "qa/integration/.../SomeIT.java",
      "first_flagged_sha": "ad7705f...",
      "flagged_jobs": ["identity-tests/identity-tests - elasticsearch9"],
      "method_last_modified_sha": null,
      "clean_runs_since_modified": 0,
      "last_observed_sha": "ad7705f...",
      "last_observed_at": "2026-06-03T11:42:00Z",
      "status": "active",
      "cleared_at": null
    }
  ]
}
```

Statuses: `active`, `cleared_via_fix`, `cleared_via_bypass`, `dropped_force_push`.

### BigQuery baseline query

The baseline returns every test that produced an unreliable result (`flaky`, `failure`, or `error`) on `main`, `stable/*`, in scheduled nightly runs, or in any other open PR over the last 20 days. PR runs are included; the current PR's own observations are excluded.

```sql
SELECT DISTINCT test_class_name, test_name
FROM   `ci-30-162810.prod_ci_analytics.test_status_v1` ts
LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs
  ON  ts.ci_url = bs.ci_url
  AND ts.build_id = bs.build_id
  AND ts.job_name = bs.job_name
  AND (
    (bs.build_trigger = "merge_group" AND (bs.build_base_ref = "refs/heads/main" OR bs.build_base_ref LIKE "refs/heads/stable/%"))
    OR (bs.build_trigger = "push"       AND (bs.build_ref      = "refs/heads/main" OR bs.build_ref      LIKE "refs/heads/stable/%"))
    OR (bs.build_trigger = "schedule"   AND (bs.build_ref      = "refs/heads/main" OR bs.build_ref      LIKE "refs/heads/stable/%"))
    OR (bs.build_trigger = "pull_request" AND bs.build_ref != @pr_ref)
  )
WHERE  ts.report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 20 DAY)
  AND  ts.ci_url = "https://github.com/camunda/camunda"
  AND  ts.test_status IN ("flaky","failure","error")
  AND  bs.ci_url IS NOT NULL
```

The trigger/ref filters live in the `ON` clause, not `WHERE`. Putting them in `WHERE` (the prior shape) silently turned the LEFT JOIN into an INNER JOIN whenever `build_status_v2` ingestion lagged `test_status_v1`, dropping known flakes from the baseline and producing false-positive alerts. The current shape preserves rows even when the matching build-status row hasn't ingested yet; the `bs.ci_url IS NOT NULL` clause then keeps only matched rows for the comparison.

### Method modification detection

For each active entry, the detector runs:

```bash
git log --format=%H -L:<method_name>:<file_path> <merge-base(base, HEAD)>..HEAD
```

Git's `-L :funcname:file` matcher uses Java-aware function-boundary detection. The first SHA output is the most recent commit that modified the method body within the range. None means no modification; the counter stays at 0.

Known limitations:

- **Java overloads** sharing a method name are treated as one unit. Modifying any overload counts as "fix attempted."
- **Renamed / moved tests** (changed class or file) are not followed. Use the bypass label.

## Operating the gate

### Monitoring

- The gate's per-PR state is in the artifact `flaky-gate-state-pr-<PR_NUMBER>` (run summary → Artifacts).
- The PR comment with the active status and counter is the user-facing summary.
- Job logs include `[new-flaky]` debug lines: baseline size, per-test decision, counter movement, drop reasons.

### Target false-positive rate

The design target is **≤ 5% false positives over a 14-day rolling window**. A "false positive" here means: the gate flagged a test that was actually known-flaky on `main`/`stable/*` or in another open PR within the prior 20 days, but didn't appear in the baseline. The most likely cause is BigQuery ingestion lag for very recent flake observations on sibling PRs.

To replay a single PR's baseline query at a chosen cutoff (to classify an alert post-hoc), see the "Running locally" section in [the action's README](https://github.com/camunda/camunda/blob/main/.github/actions/detect-new-flaky-tests/README.md#running-locally).

### When the gate fires on your PR

1. **Look at the comment first.** It tells you which test, which job, and how many clean re-runs you have.
2. **If the test name is from your diff**, you introduced it — fix the method and push. The next 3 clean CI runs will clear it.
3. **If you didn't touch anything related**, it's a pre-existing flake that just hit you. Apply `ci:flaky-test-bypass` (after opening a `kind/flake` issue) and re-run CI.
4. **If you're convinced it's a false positive from the gate itself** (e.g. the test has clearly flaked on `main` recently), confirm with the BigQuery replay query and reach out to `#ask-monorepo-devops`. The bypass label always works as an escape hatch.

### Disabling the gate temporarily

Single-line revert in `.github/workflows/ci.yml`:

```diff
-          blocking: 'true'
+          blocking: 'false'
```

This keeps the sticky logic and comments but stops the gate from failing `check-results`. Useful if the gate misbehaves at scale and you need to unblock everyone while the root cause is investigated. The state artifact and comment continue to update normally.

## History and related work

- **Initial gate** introduced in [#50691](https://github.com/camunda/camunda/pull/50691) — "feat: Add logic to filter out new flaky tests."
- **20-day baseline + PR-runs inclusion** added in [#53121](https://github.com/camunda/camunda/pull/53121).
- **Renovate exclusion** added in [#53683](https://github.com/camunda/camunda/pull/53683). 6-day audit showed 0 Renovate alerts post-merge — exclusion fully effective.
- **Sticky alerts + BQ LEFT-JOIN fix** added in [#54579](https://github.com/camunda/camunda/pull/54579). Closes the retry-loophole and the ingestion-lag FP source.

## Related documentation

- [Action README](https://github.com/camunda/camunda/blob/main/.github/actions/detect-new-flaky-tests/README.md) — implementation deep-dive, all inputs/outputs, local-run instructions.
- [CI & Automation › Flaky tests](./ci.md#flaky-tests) — broader context on how Maven test reruns and flaky-test-extractor classify results.
- [CI Runbooks](./ci-runbooks.md) — what to do when CI alerts fire.
- [CI Health metrics](./ci.md#ci-health-metrics) — the BigQuery analytics layer the gate queries.
