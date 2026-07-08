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

This "re-runs count" guarantee depends on where state is stored. State lives
in the gate's **PR comment** (a hidden base64 marker), not a workflow
artifact. GitHub's "Re-run jobs" button and force-pushes both **delete
run-scoped artifacts**, so an artifact-based counter silently reset on every
re-run and could only advance via new distinct runs (throwaway commits).
The comment is durable across re-runs, attempts, and
force-pushes, so a fixed test genuinely clears by re-running CI.

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
│        ├─ Run detect_new_flaky_tests.py                              │
│        │  ├─ Read prior state from the PR comment (or fresh)         │
│        │  ├─ No prior state + no flakes + no bypass? → no-op         │
│        │  ├─ Bypass label?  → mark all active → cleared_via_bypass   │
│        │  ├─ Else:                                                   │
│        │  │    1. Reconcile force-push (drop unreachable+absent)     │
│        │  │    2. Re-evaluate method modification via git log -L     │
│        │  │    3. Filter BQ-new flakes via touch-check + blank-class │
│        │  │    4. Merge surviving new flakes / reset re-flaked       │
│        │  │    5. Increment counters where job ran AND test clean    │
│        │  │    6. Promote counter>=3 entries to cleared_via_fix      │
│        │  └─ Render comment (state embedded) + upsert PR comment     │
│        └─ Exit non-zero if any entries remain active (blocking)      │
└──────────────────────────────────────────────────────────────────────┘
```

## State schema

Per-PR JSON embedded (base64) in the hidden `<!-- flaky-gate-state: … -->`
marker of the gate's PR comment — the durable store (see "Why the PR comment
holds state" below). The detector reads it back on the next run via the GitHub
comments API.

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

## Why the PR comment holds state

State was previously a per-PR workflow artifact (`flaky-gate-state-pr-<N>`,
selected newest-by-`created_at`). The root cause: GitHub's "Re-run jobs"
button starts a new run attempt and **deletes the run's own prior-attempt
artifacts**, and `upload-artifact overwrite` is run-scoped. So a re-run could
not see the counter its earlier attempt banked — it fell back to an older
distinct run's artifact and re-landed on the same value. Documented "re-runs
count" clearance was therefore impossible; only new distinct runs (throwaway
commits) advanced the counter. No artifact-selection strategy fixes this,
because the advanced state only ever lived in the deleted artifact.

The PR comment is **not** run-scoped: it survives re-runs, attempts, and
force-pushes. Storing state in it makes the counter durable and monotonic
across every kind of subsequent run, so a genuinely fixed test clears by
re-running CI — no empty commits. It also removed the artifact entirely
(no retention window, no `actions:` permission).

Trade-offs (accepted for a pilot advisory gate):

- **Visibility / tamper** — the base64 state is present in the comment source;
  a maintainer could edit it. The gate is advisory and already has a
  `ci:flaky-test-bypass` label escape hatch, so this is not a new trust
  boundary.
- **Concurrency** — two runs finishing at once do last-writer-wins on the
  comment (same race the artifact had). `MIN_CLEAN_RUNS = 3` absorbs a rare
  lost increment.
- **Size** — base64 state is well under GitHub's 65 536-char comment cap for
  realistic per-PR test counts.

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

## Per-test verify query in the comment

Each active alert in the PR comment carries a collapsible `Verify in
BigQuery` block with a ready-to-run query scoped to that one test
(`test_class_name` + `test_name`, last 60 days). The author runs it to see
whether the test already produced `flaky`/`failure`/`error` rows on
`main`/`stable/*` or other PRs — prior rows mean a pre-existing intermittent
flake (likely a false alarm); no rows mean this PR genuinely introduced it.
The FQN is read from the sticky state entry, so the block survives re-runs.

## False-positive suppression

A post-merge pilot audit (618 PRs, 2026-06-04 → 2026-06-15) found a 56%
false-positive rate. All false positives shared the same pattern:
a pre-existing infra-flaky test (GCS testcontainer, disk-space cluster,
raft failover, OpenSearch indexing) flaked during a CI run on a PR that
had no relation to the test's code. The BigQuery baseline correctly contained
those tests, but the method-keyed matching failed for two reasons explained
below. Two filters address this before any new flake enters the sticky state.

### Filter 1 — Package touch-check

`get_pr_changed_paths()` resolves the diff base via `get_merge_base()` (which
tries `BASE_SHA` first, then `origin/<base_ref>`, so it also works in PR
merge-ref checkouts) and runs:

```bash
git diff --name-only <merge_base>...<head_sha>
```

and extracts the Java package path from every changed `.java` file
(e.g. `zeebe/backup-gcs/src/main/java/io/camunda/zeebe/backup/gcs/Foo.java`
→ `io/camunda/zeebe/backup/gcs`).

`filter_by_touch_check()` then silences any new-flaky alert whose Java
package was not touched by the PR — either an exact match, or the PR touched
a parent package (production code above the test). A test in
`io.camunda.zeebe.backup.gcs` cannot have been broken by a PR that only
changes `io.camunda.zeebe.agent`.

The reverse "PR changed a strict sub-package of the test's package" match is
intentionally **not** used: for shallow/root test packages such as
`io.camunda` virtually every file in the monorepo is a descendant, so that
rule degenerated to always-true and defeated the filter (it flagged the
root-package `InvoiceDecisionTest` on unrelated PRs — #55489). A 60-day replay
of every known true positive confirmed none relied on the descendant match.

A PR that changes only YAML/config files (no `.java` at all) produces an
empty `changed_pkg_paths` set with `available=True` — that is still a valid
no-touch signal and suppresses all alerts. If the git diff command fails
entirely, `available=False` is returned and the filter is skipped to avoid
accidental suppression.

### Filter 2 — Blank-class fallback

Class/container-level failures (`@BeforeAll`, testcontainer startup) are
recorded in `test_status_v1` with an empty `test_name`. The method-keyed
baseline key becomes `"…ClassName."` (trailing dot) and never matches a
real method, so the gate treats those methods as "new" on any PR that
happens to be running when the container crashes.

`load_baseline_keys()` now also returns `blank_class_fqns`: the set of
class FQNs with at least one blank-`test_name` entry in the 20-day
baseline. When the PR's package _does_ match (Filter 1 passes), but the
class is in `blank_class_fqns` AND the test file itself was not modified by
the PR, the alert is suppressed. The test file check is the key
safety guard: if the PR directly edits the test, the alert fires regardless
of infra-failure history.

### Filter interaction and safety

Both filters apply only to tests that are not yet tracked in the sticky
state — they gate new entries into `merge_new_flakes`, not existing ones.

True positives are unaffected by design:

|               True positive                |                Why filter does not suppress                |
|--------------------------------------------|------------------------------------------------------------|
| PR adds a new test class                   | Class file is in the diff → package matches + file matches |
| PR modifies the test file                  | Test file is in the diff → blank-class check bypassed      |
| PR modifies the production code under test | Same package → Filter 1 passes, alert fires                |

## Method modification detection

For each active entry, the detector runs:

```bash
git log --format=%H -L:<method_name>[(]:<file_path> <merge-base(base, HEAD)>..HEAD
```

Git's `-L :funcname:file` matcher uses language-aware function-boundary
detection (Java's default funcname regex). The first SHA output is the
most-recent commit that modified the method body within the range. None
means no modification, so the counter stays at 0.

The `[(]` suffix anchors the regex to the method **declaration** by requiring
the name to be immediately followed by `(` (google-java-format leaves no space
before the parameter list). Without it, a short name matches inside a longer
method header sharing its prefix — a query for `getVersion` would also match
`getVersionLowerCase(`, and `-L` locks onto whichever declaration appears
first, tracking the wrong method. The `[(]` character class is used rather than
an escaped `\(`, which git's `-L` parser rejects with "parentheses not
balanced".

This detection depends on git's Java funcname driver being active. The repo
has no `*.java diff=java` gitattributes, so the composite action enables the
built-in driver for the gate's checkout only (appends `*.java diff=java` to
`.git/info/attributes`, which is local and not committed) and then verifies it
took effect via `git check-attr diff`, failing loud otherwise. See
`action.yml`.

Known limitations:

- **Java overloads** share a method name; since all overloads share `name(`,
  they are treated as a single unit. Modifying any overload of the same name
  counts as "fix attempted." Worth noting in PR review but rarely problematic
  in practice.
- **Renamed / moved tests** that change file or class are not followed by
  `-L`. Such a change also re-keys the entry (the key is
  `package.class.method`), so the test simply re-alerts fresh; use
  `ci:flaky-test-bypass` if that is unwanted.

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

Single PR comment, identified by `<!-- new-flaky-tests-alert -->`. The second
line carries the full sticky state as a base64-encoded hidden marker (invisible
in the rendered comment); the detector reads it back on the next run:

```
<!-- new-flaky-tests-alert -->
<!-- flaky-gate-state: eyJzY2hlbWFfdmVyc2lvbiI6MSwicHJfbnVtYmVy… -->
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
2. **Build `ran-jobs-json`** from `needs.<job>.result` values (any value
   other than `skipped`).
3. **Read bypass label** from `pull_request.labels`.
4. **Query BigQuery** only when this run produced new flakes.
5. **Invoke the composite action** with all of the above plus
   `head-sha`, `base-ref`, `blocking: 'true'`.

The action needs only `pull-requests: write` + `contents: read`. Prior state
is read from the gate's PR comment at the start of the detector and the updated
state is written back into that comment at the end — there is no artifact
download/upload step and no `actions:` permission.

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

|      Secret       |                        Source                        |             Purpose              |
|-------------------|------------------------------------------------------|----------------------------------|
| `VAULT_ADDR`      | Repository secret                                    | Vault server URL                 |
| `VAULT_ROLE_ID`   | Repository secret                                    | Vault AppRole auth               |
| `VAULT_SECRET_ID` | Repository secret                                    | Vault AppRole auth               |
| GCP SA key        | Vault (`secret/data/products/zeebe/ci/ci-analytics`) | BigQuery access                  |
| `GITHUB_TOKEN`    | Auto                                                 | Comment read/write (state store) |

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

# 2. Optional: provide a prior state file. In CI, prior state is read from the
#    PR comment marker; STATE_FILE_IN is only the local/offline fallback used
#    when the comment cannot be fetched (e.g. a fake token, as below).
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

