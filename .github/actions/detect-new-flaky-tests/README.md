# Detect New Flaky Tests

A CI quality gate that blocks PRs from introducing **new** flaky tests, with
**sticky alerts**: once a test is flagged on a PR, the alert remains until either
the test method is actually fixed (and proven by 3 clean re-runs) or the
`ci:flaky-test-bypass` label is applied. A test passing on retry alone does
**not** clear the alert.

## Why sticky?

Before stickiness, the bot's "Resolved" comment was produced whenever a
subsequent CI run found no new flakes — i.e., any retry-luck pass would
silence the alert. Real flakes routinely got merged because authors hit
"Re-run jobs" until CI went green. Sticky alerts close that loophole.

## Clearance rules

An alert entry transitions from `active` → cleared when one of:

|      Path       |                                                                 Trigger                                                                 |   Resulting status   |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| Method fix      | The flagged test's method body is modified, AND the affected job runs clean (no Maven retries) for at least **3** subsequent gate runs. | `cleared_via_fix`    |
| Bypass label    | `ci:flaky-test-bypass` is applied to the PR.                                                                                            | `cleared_via_bypass` |
| Force-push drop | The PR is rewritten so the original flagging commit is no longer reachable, AND the test does not flake in the new history.             | `dropped_force_push` |

"Clean run" means **Def-2**: the test was actually observed in this run AND
did not appear in `FLAKY.xml`. A cancelled or skipped job does not advance
the counter. A retry-pass within Maven still counts as a flake (entry in
`FLAKY.xml`), which resets the counter to 0.

The counter is per-test and only counts runs in the **same parent job** as
the original flake (matrix entries are normalised to the parent — e.g.
`identity-tests/identity-tests - elasticsearch9` rolls up to
`identity-tests`). Re-runs of the same SHA count just like runs on later
commits, so authors aren't forced to push throwaway commits to bank
evidence — but the gate is only reachable after a real modification, so
re-run-spam-without-fix can't game it.

## How it works

```
┌──────────────────────────────────────────────────────────────────────┐
│                        CI pipeline (ci.yml)                          │
│                                                                      │
│  1. Test jobs run; FLAKY.xml extracted; collect-flaky-tests          │
│     produces flaky_tests_data (JSON).                                │
│                                                                      │
│  2. detect-new-flaky-tests job                                       │
│     ├─ Build ran-jobs JSON from needs.X.result                       │
│     ├─ Read bypass label                                             │
│     ├─ Query BigQuery for baseline (only if new flakes this run)     │
│     └─ detect-new-flaky-tests action (composite)                     │
│        ├─ Discover + download prior state artifact (or fresh)        │
│        ├─ Run detect_new_flaky_tests.py                              │
│        │  ├─ No prior state + no flakes + no bypass? → no-op         │
│        │  ├─ Bypass label?  → mark all active → cleared_via_bypass   │
│        │  ├─ Else:                                                   │
│        │  │    1. Reconcile force-push (drop unreachable+absent)     │
│        │  │    2. Re-evaluate method modification via git log -L     │
│        │  │    3. Merge new flakes / reset re-flaked entries         │
│        │  │    4. Increment counters where job ran AND test clean    │
│        │  │    5. Promote counter>=3 entries to cleared_via_fix      │
│        │  └─ Render comment, write state.json                        │
│        ├─ Upload state artifact (flaky-gate-state-pr-<N>, 30d)       │
│        └─ Exit non-zero if any entries remain active (blocking)      │
└──────────────────────────────────────────────────────────────────────┘
```

## State schema

Per-PR JSON written to / read from the workflow artifact
`flaky-gate-state-pr-<PR_NUMBER>` (retention 30 days).

```json
{
  "schema_version": 1,
  "pr_number": 54375,
  "last_known_head_sha": "ad7705f...",
  "last_updated_at": "2026-06-03T11:42:00Z",
  "tests": [
    {
      "key": "io.camunda.it.auth.ProcessDefinitionStatisticsAuthorizationIT.shouldAllowReadProcessInstancePermission",
      "package": "io.camunda.it.auth",
      "class_name": "ProcessDefinitionStatisticsAuthorizationIT",
      "method_name": "shouldAllowReadProcessInstancePermission",
      "file_path": "qa/integration/src/test/java/io/camunda/it/auth/ProcessDefinitionStatisticsAuthorizationIT.java",
      "first_flagged_sha": "ad7705f447bdcb2b9f5c813e85a3ec55b4efd1ca",
      "flagged_jobs": ["identity-tests/identity-tests - elasticsearch9"],
      "method_last_modified_sha": null,
      "clean_runs_since_modified": 0,
      "last_observed_sha": "ad7705f447bdcb2b9f5c813e85a3ec55b4efd1ca",
      "last_observed_at": "2026-06-03T11:42:00Z",
      "status": "active",
      "cleared_at": null
    }
  ]
}
```

Statuses: `active`, `cleared_via_fix`, `cleared_via_bypass`,
`dropped_force_push`.

## BigQuery baseline query

Returns every test that produced an unreliable result (`flaky`, `failure`,
or `error`) on `main`, `stable/*`, in scheduled nightly runs, or in any
other open PR over the last 20 days. PR runs are included but the current
PR's own observations are excluded.

The trigger/ref filters live in the LEFT JOIN's `ON` clause, not the
`WHERE`. Keeping them in `WHERE` turned the LEFT JOIN into an effective
INNER JOIN whenever `build_status_v2` ingestion lagged behind
`test_status_v1`, dropping known flakes from the baseline and producing
false positives. The current shape preserves rows even when the matching
build-status row hasn't ingested yet; we then require `bs.ci_url IS NOT
NULL` to keep only matched rows.

```sql
SELECT DISTINCT test_class_name, test_name
FROM   `ci-30-162810.prod_ci_analytics.test_status_v1` ts
LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs
  ON  ts.ci_url = bs.ci_url
  AND ts.build_id = bs.build_id
  AND ts.job_name = bs.job_name
  AND (
    (bs.build_trigger = "merge_group"   AND (bs.build_base_ref = "refs/heads/main" OR bs.build_base_ref LIKE "refs/heads/stable/%"))
    OR (bs.build_trigger = "push"       AND (bs.build_ref      = "refs/heads/main" OR bs.build_ref      LIKE "refs/heads/stable/%"))
    OR (bs.build_trigger = "schedule"   AND (bs.build_ref      = "refs/heads/main" OR bs.build_ref      LIKE "refs/heads/stable/%"))
    OR (bs.build_trigger = "pull_request" AND bs.build_ref != @pr_ref)
  )
WHERE  ts.report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 20 DAY)
  AND  ts.ci_url = "https://github.com/camunda/camunda"
  AND  ts.test_status IN ("flaky","failure","error")
  AND  bs.ci_url IS NOT NULL
```

## Method modification detection

For each active entry, the detector runs:

```bash
git log --format=%H -L:<method_name>:<file_path> <merge-base(base, HEAD)>..HEAD
```

Git's `-L :funcname:file` matcher uses language-aware function-boundary
detection (Java's default funcname regex). The first SHA output is the
most-recent commit that modified the method body within the range. None
means no modification, so the counter stays at 0.

Known limitations:

- **Java overloads** share a method name; they are treated as a single unit.
  Modifying any overload of the same name counts as "fix attempted." Worth
  noting in PR review but rarely problematic in practice.
- **Renamed / moved tests** that change file or class are not followed.
  Use `ci:flaky-test-bypass` for those.

## Per-test counter semantics

Increments happen in `increment_counters_if_clean` and follow these rules:

1. The entry must be `active` and have a non-null `method_last_modified_sha`
   (the gate believes a fix attempt exists).
2. The test must NOT be in this run's `FLAKY.xml` (re-flake → reset to 0).
3. At least one of the entry's `flagged_jobs` parent names must appear in
   `RAN_JOBS_JSON` (the originally affected job actually ran).
4. If all three hold, counter += 1.
5. Counter `>= 3` (`MIN_CLEAN_RUNS`) promotes the entry to
   `cleared_via_fix`.

## Force-push semantics

Always re-evaluated against the current branch state — no special
detection. For each entry:

- If `first_flagged_sha` is no longer reachable from `HEAD` AND the test is
  not in the current run's flakes → entry is `dropped_force_push`.
- Otherwise: re-run `git log -L` against
  `merge-base(base_ref, HEAD)..HEAD`. If a modification is still findable,
  `method_last_modified_sha` is updated (may change SHA if a new commit
  touched the method — this resets the counter via Q4c).
- This handles both legitimate rebases (modification preserved → counter
  preserved) and force-push-to-silence attacks (modification not found →
  counter stays at 0, alert stays sticky).

## Comment rendering

Single PR comment, identified by `<!-- new-flaky-tests-alert -->`. Carries
a hidden pointer to the state artifact:

```
<!-- new-flaky-tests-alert -->
<!-- flaky-gate-state-artifact: flaky-gate-state-pr-54375 -->
```

### Active alert

```
# ⚠️ New Flaky Tests Detected

This PR introduces **2 new flaky test(s)** ...

- **shouldFlushBatchWhenFull**
  - Jobs: `general-unit-tests`
  - Package: `io.camunda.exporter.appint.subscription`
  - Class: `SubscriptionTest`
  - State:
    - First flagged at: `abc1234`
    - Method last modified at: `def5678`
    - Clean re-runs since fix: 2 / 3
    - Last observed: 2026-06-03 09:42:00 UTC
```

### All cleared

```
# ✅ Cleared — No outstanding new flakes

All previously flagged tests cleared via fix + 3 clean re-runs, or via
`ci:flaky-test-bypass` label.

<details>
<summary>Previous warning</summary>
... strikethrough history ...
</details>
```

The word "Cleared" intentionally replaces the legacy "Resolved" — the old
"Resolved" template fired on any retry-luck pass, and reusing the same
headline would conflate the two behaviors.

## Workflow integration

See `.github/workflows/ci.yml` `detect-new-flaky-tests` job. Key steps:

1. **`actions/checkout`** with `fetch-depth: 0` (full history required for
   `git log -L`).
2. **Discover and download** the latest non-expired
   `flaky-gate-state-pr-<N>` artifact via `gh api`.
3. **Build `ran-jobs-json`** from `needs.<job>.result` values (any value
   other than `skipped`).
4. **Read bypass label** from `pull_request.labels`.
5. **Query BigQuery** only when this run produced new flakes.
6. **Invoke the composite action** with all of the above plus
   `head-sha`, `base-ref`, `blocking: 'true'`.
7. **Upload** the new `state.json` as
   `flaky-gate-state-pr-<N>` (retention 30 days, overwrite previous).

## When the gate runs

The job runs on `pull_request` events from non-fork repos, EXCEPT:

- PRs from `monorepo-devops-automation[bot]` (backport bot).
- PRs from `renovate[bot]` (dependency-bump bot — see PR #53683 for
  rationale: dependency bumps don't introduce flakes; any flake hit is
  pre-existing infra noise).
- PRs whose `head_ref` starts with `backport` (manually-named backports).

Direct fixes pushed to `stable/*` branches by humans still run the gate.

## Bypass label workflow

If the gate flags a flaky test that is **not caused by your changes**:

1. Apply the `ci:flaky-test-bypass` label to your PR.
2. Re-run CI — the next gate run will mark all active entries as
   `cleared_via_bypass` and post the "Cleared" comment.

> Before bypassing, create a `kind/flake` issue documenting the test, the
> job, and the CI run URL. The bypass label is not a fix.

## Inputs

|          Input           | Required |       Default       |                                                Description                                                |
|--------------------------|----------|---------------------|-----------------------------------------------------------------------------------------------------------|
| `pr-flaky-tests-data`    | yes      | —                   | JSON `[{job, flaky_tests}]` from `collect-flaky-tests`.                                                   |
| `known-flaky-tests-file` | yes      | —                   | Path to BigQuery baseline JSON.                                                                           |
| `pr-number`              | yes      | —                   | Pull request number.                                                                                      |
| `blocking`               | no       | `true`              | Fail the job if any sticky entry is still active.                                                         |
| `ran-jobs-json`          | no       | `[]`                | JSON list of parent job names whose result is not `skipped`.                                              |
| `bypass-label-present`   | no       | `false`             | `true` if `ci:flaky-test-bypass` is on the PR.                                                            |
| `head-sha`               | yes      | —                   | Current PR head SHA.                                                                                      |
| `base-ref`               | yes      | —                   | PR base branch — fallback merge-base candidate for `git log -L`.                                          |
| `base-sha`               | no       | `''`                | PR base commit SHA — preferred merge-base candidate (base/origin refs absent in a PR merge-ref checkout). |
| `repo-root`              | no       | `$GITHUB_WORKSPACE` | Absolute path to the checked-out repo.                                                                    |

## Outputs

|        Output         |                              Description                              |
|-----------------------|-----------------------------------------------------------------------|
| `has-new-flaky-tests` | `true` if any entries remain in `active` status after reconciliation. |

## Secrets

|      Secret       |                        Source                        |        Purpose         |
|-------------------|------------------------------------------------------|------------------------|
| `VAULT_ADDR`      | Repository secret                                    | Vault server URL       |
| `VAULT_ROLE_ID`   | Repository secret                                    | Vault AppRole auth     |
| `VAULT_SECRET_ID` | Repository secret                                    | Vault AppRole auth     |
| GCP SA key        | Vault (`secret/data/products/zeebe/ci/ci-analytics`) | BigQuery access        |
| `GITHUB_TOKEN`    | Auto                                                 | Comment + artifact API |

## Running locally

```bash
# 1. Pull baseline from BigQuery (bq CLI must be authenticated).
bq query --project_id=ci-30-162810 --use_legacy_sql=false --format=json \
  --parameter='pr_ref:STRING:refs/pull/12345/merge' \
  'SELECT DISTINCT test_class_name, test_name
   FROM `ci-30-162810.prod_ci_analytics.test_status_v1` ts
   LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs
     ON ts.ci_url=bs.ci_url AND ts.build_id=bs.build_id AND ts.job_name=bs.job_name
     AND (
       (bs.build_trigger="merge_group" AND (bs.build_base_ref="refs/heads/main" OR bs.build_base_ref LIKE "refs/heads/stable/%"))
       OR (bs.build_trigger="push" AND (bs.build_ref="refs/heads/main" OR bs.build_ref LIKE "refs/heads/stable/%"))
       OR (bs.build_trigger="schedule" AND (bs.build_ref="refs/heads/main" OR bs.build_ref LIKE "refs/heads/stable/%"))
       OR (bs.build_trigger="pull_request" AND bs.build_ref != @pr_ref)
     )
   WHERE ts.report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 20 DAY)
     AND ts.ci_url="https://github.com/camunda/camunda"
     AND ts.test_status IN ("flaky","failure","error")
     AND bs.ci_url IS NOT NULL' > /tmp/known-flaky-tests.json

# 2. Optional: provide a prior state file.
echo '{"schema_version":1,"pr_number":12345,"last_known_head_sha":"prev","last_updated_at":"t","tests":[]}' > /tmp/state-in.json

# 3. Simulate PR flaky data and run the detector.
PR_FLAKY_TESTS_DATA='[{"job":"general-unit-tests","flaky_tests":"io.camunda.it.example.ExampleIT.shouldDoX(Param)"}]' \
KNOWN_FLAKY_TESTS_FILE=/tmp/known-flaky-tests.json \
PR_NUMBER=12345 \
STATE_FILE_IN=/tmp/state-in.json \
STATE_FILE_OUT=/tmp/state-out.json \
RAN_JOBS_JSON='["general-unit-tests"]' \
BYPASS_LABEL_PRESENT=false \
HEAD_SHA=$(git rev-parse HEAD) \
BASE_REF=main \
BLOCKING=true \
REPO_ROOT=$(pwd) \
GITHUB_TOKEN=fake \
GITHUB_REPOSITORY=camunda/camunda \
python3 .github/actions/detect-new-flaky-tests/detect_new_flaky_tests.py

# 4. Inspect the resulting state.
cat /tmp/state-out.json | jq .
```

## Tests

Unit tests live in `test_detect_new_flaky_tests.py`. Run with:

```bash
cd .github/actions/detect-new-flaky-tests
python3 -m unittest test_detect_new_flaky_tests -v
```

Covers: name parsing, dedup, baseline boundary matching, state persistence
roundtrip, schema-mismatch reset, new-flake addition, re-flake reset for
active and cleared entries, counter increment under all combinations of
modification/job-ran/test-clean, clearance at threshold, bypass label,
force-push drop, Q4c counter reset on additional modification, and comment
rendering (active / mixed / all-clear).

## File structure

```
.github/actions/detect-new-flaky-tests/
├── action.yml                       # Composite action definition
├── detect_new_flaky_tests.py        # Sticky-alert detection logic
├── test_detect_new_flaky_tests.py   # Unit tests (stdlib only)
└── README.md                        # This file
```

