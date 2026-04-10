# Detect New Flaky Tests

A CI quality gate that prevents PRs from introducing **new** flaky tests.
It compares flaky tests detected in the PR's CI run against a baseline of
tests that are already known to be flaky on `main` and `stable/*` branches.

Only truly **new** flaky tests block the PR — pre-existing flaky tests are ignored.

## How It Works

```
┌──────────────────────────────────────────────────────────────────────┐
│                        CI pipeline (ci.yml)                          │
│                                                                      │
│  1. Test jobs run (unit, integration, RDBMS, etc.)                   │
│     └─ Maven rerun count = 3 for PRs                                 │
│     └─ Flaky tests produce FLAKY.xml via analyze-test-runs           │
│                                                                      │
│  2. utils-flaky-tests-summary                                        │
│     └─ collect-flaky-tests gathers all FLAKY.xml results             │
│     └─ Outputs: flaky_tests_data (JSON)                              │
│                                                                      │
│  3. detect-new-flaky-tests  ← THIS ACTION                            │
│     ├─ Checks for `ci:flaky-test-bypass` label → skip if present     │
│     ├─ Queries BigQuery for baseline (known flaky tests)             │
│     ├─ Compares PR flaky tests against baseline                      │
│     ├─ If new flaky tests found → posts comment + fails              │
│     └─ If all known or bypassed → passes                             │
│                                                                      │
│  4. check-results                                                    │
│     └─ Aggregates all job results including detect-new-flaky-tests   │
└──────────────────────────────────────────────────────────────────────┘
```

## Data Flow

### Input 1: PR flaky tests (`PR_FLAKY_TESTS_DATA`)

Comes from the `collect-flaky-tests` action. This is a JSON array of objects,
one per CI job that had flaky tests:

```json
[
  {
    "job": "rdbms-integration-tests",
    "flaky_tests": "io.camunda.it.rdbms.db.processinstance.ProcessInstanceIT.shouldSelectRootExpiredRootProcessInstances(CamundaRdbmsTestApplication)[5]"
  }
]
```

### Input 2: Baseline flaky tests (`KNOWN_FLAKY_TESTS`)

Queried from BigQuery at runtime. Contains all tests that have been flaky on
`main` or `stable/*` in the last 60 days:

```json
[
  {
    "test_class_name": "io.camunda.it.rdbms.db.processinstance.ProcessInstanceIT",
    "test_name": "shouldSelectRootExpiredRootProcessInstances(CamundaRdbmsTestApplication) camundaWithOracleDB"
  }
]
```

### BigQuery Query

```sql
SELECT DISTINCT test_class_name, test_name
FROM `ci-30-162810.prod_ci_analytics.test_status_v1` ts
LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs
  ON ts.ci_url = bs.ci_url AND ts.build_id = bs.build_id AND ts.job_name = bs.job_name
WHERE ts.report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 60 DAY)
  AND ts.ci_url = "https://github.com/camunda/camunda"
  AND test_status = "flaky"
  AND (
    (build_trigger = "merge_group"
      AND (build_base_ref = "refs/heads/main" OR build_base_ref LIKE "refs/heads/stable/%"))
    OR
    (build_trigger = "push"
      AND (build_ref = "refs/heads/main" OR build_ref LIKE "refs/heads/stable/%"))
  )
```

This returns every test that flaked at least once on `main` or `stable/*` in
the last 60 days. The GCP service account key is fetched from Vault.

## Processing Steps

### Step 1: Parse PR flaky tests

The raw `flaky_tests` string from each job is split into individual test names.
Each test name is parsed into its components:

|                                                        Raw test name from Maven                                                        |                                                              Parsed                                                               |
|----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `io.camunda.it.rdbms.db.processinstance.ProcessInstanceIT.shouldSelectRootExpiredRootProcessInstances(CamundaRdbmsTestApplication)[5]` | package=`io.camunda.it.rdbms.db.processinstance`, class=`ProcessInstanceIT`, method=`shouldSelectRootExpiredRootProcessInstances` |

The `(CamundaRdbmsTestApplication)` (parameter info) and `[5]` (index) are
stripped during parsing. This produces a **normalized key**:

```
io.camunda.it.rdbms.db.processinstance.ProcessInstanceIT.shouldSelectRootExpiredRootProcessInstances
```

If the same test appears in multiple jobs, it's deduplicated (jobs are merged).

### Step 2: Parse baseline flaky tests

BigQuery results are built into **baseline keys** by concatenating
`test_class_name` + `.` + `test_name` **without any normalization**:

```
io.camunda.it.rdbms.db.processinstance.ProcessInstanceIT.shouldSelectRootExpiredRootProcessInstances(CamundaRdbmsTestApplication) camundaWithOracleDB
```

These raw keys preserve parameter info and database variant suffixes.

### Step 3: Compare (boundary-aware matching)

For each PR flaky test, we check if any baseline key refers to the same test
method. The challenge is that the two sides have **different formats**:

|        Side         |            Key format             |                                         Example                                          |
|---------------------|-----------------------------------|------------------------------------------------------------------------------------------|
| **PR** (normalized) | `pkg.Class.method`                | `...ProcessInstanceIT.shouldSelectRoot`                                                  |
| **Baseline** (raw)  | `pkg.Class.method(Param) variant` | `...ProcessInstanceIT.shouldSelectRoot(CamundaRdbmsTestApplication) camundaWithOracleDB` |

The matching function `_is_same_test(baseline_key, normalized_key)` works as
follows:

1. **Exact match?** If `baseline_key == normalized_key` → match.
2. **Starts with?** If `baseline_key.startswith(normalized_key)` → check step 3.
3. **Boundary check:** Look at the character immediately after the normalized
   key ends. If it's a **non-alphanumeric, non-underscore** character (like `(`
   or ` `), it's a boundary → match. If it's a letter, digit, or `_`, it means
   we matched a prefix of a *different* method name → no match.

### Boundary matching examples

|    Normalized key     |             Baseline key             | Next char | Match? |                    Why                    |
|-----------------------|--------------------------------------|-----------|--------|-------------------------------------------|
| `...shouldSelectRoot` | `...shouldSelectRoot`                | *(end)*   | ✅      | Exact match                               |
| `...shouldSelectRoot` | `...shouldSelectRoot(Param) variant` | `(`       | ✅      | `(` is a boundary                         |
| `...shouldSelectRoot` | `...shouldSelectRoot[5]`             | `[`       | ✅      | `[` is a boundary                         |
| `...shouldFind`       | `...shouldFindAllItems(Param)`       | `A`       | ❌      | `A` is alphanumeric → different method    |
| `...shouldFind`       | `...shouldFind_v2(Param)`            | `_`       | ❌      | `_` is identifier char → different method |

### Decision

|                Scenario                 |                                       Result                                        |
|-----------------------------------------|-------------------------------------------------------------------------------------|
| All PR flaky tests match a baseline key | ✅ **Pass** — sets `has-new-flaky-tests=false`                                       |
| At least one PR flaky test has no match | ❌ **Fail** — posts a PR comment, sets `has-new-flaky-tests=true`, exits with code 1 |

## Bypass Label

If the gate flags a flaky test that is **not caused by your changes**, you can
skip the gate by adding the `ci:flaky-test-bypass` label to your PR and
re-running CI.

When the label is present, the `detect-new-flaky-tests` job prints a notice
and exits successfully without querying BigQuery or comparing tests.

> **Important:** Before bypassing, create a `kind/flake` issue so the flaky
> test is tracked and eventually fixed.

## PR Comment

When new flaky tests are found, the action posts a comment like:

> ## ⚠️ New Flaky Tests Detected
>
> This PR introduces **1 new flaky test(s)** that are not currently flaky on
> `main` or `stable/*` branches.
>
> - **shouldDoSomething**
>   - Jobs: `rdbms-integration-tests`
>   - Package: `io.camunda.it.example`
>   - Class: `ExampleIT`
>   - Retries in this run: 1
>
> ---
>
> **What to do:**
> 1. Check if the flaky test is caused by your changes and fix it
> 2. If the test is unrelated to your changes:
> - Contact the monorepo devops (#top-monorepo-ci) to triage and discuss the next steps
> - Create an issue with the `kind/flake` label documenting the test, job, and a link to this CI run
> - Add the `ci:flaky-test-bypass` label to this PR to skip the gate and unblock merging
> - Re-run CI after adding the label

## CI Job Configuration

The `detect-new-flaky-tests` job is defined in `.github/workflows/ci.yml`:

- **Runs on:** `ubuntu-latest`
- **Timeout:** 10 minutes
- **Condition:** Only on `pull_request` events from non-fork repos
- **Depends on:** `utils-flaky-tests-summary` (which collects flaky test data
  from all test jobs)
- **Part of:** `check-results` aggregation (failures block merge)

### Secrets used

|      Secret       |                        Source                        |      Purpose       |
|-------------------|------------------------------------------------------|--------------------|
| `VAULT_ADDR`      | Repository secret                                    | Vault server URL   |
| `VAULT_ROLE_ID`   | Repository secret                                    | Vault AppRole auth |
| `VAULT_SECRET_ID` | Repository secret                                    | Vault AppRole auth |
| GCP SA key        | Vault (`secret/data/products/zeebe/ci/ci-analytics`) | BigQuery access    |

## Running Locally

You can test the script locally if you have `bq` CLI authenticated:

```bash
# 1. Query BigQuery for baseline (or use a subset)
KNOWN=$(bq query --project_id=ci-30-162810 --use_legacy_sql=false --format=json \
  'SELECT DISTINCT test_class_name, test_name
   FROM `ci-30-162810.prod_ci_analytics.test_status_v1` ts
   LEFT OUTER JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs
     ON ts.ci_url=bs.ci_url AND ts.build_id=bs.build_id AND ts.job_name=bs.job_name
   WHERE ts.report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 60 DAY)
     AND ts.ci_url="https://github.com/camunda/camunda"
     AND test_status = "flaky"
     AND (
       (build_trigger="merge_group" AND (build_base_ref="refs/heads/main" OR build_base_ref LIKE "refs/heads/stable/%"))
       OR (build_trigger="push" AND (build_ref="refs/heads/main" OR build_ref LIKE "refs/heads/stable/%"))
     )' 2>/dev/null)

# 2. Simulate PR flaky test data
PR_DATA='[{"job":"rdbms-integration-tests","flaky_tests":"io.camunda.it.rdbms.db.processinstance.ProcessInstanceIT.shouldSelectRootExpiredRootProcessInstances(CamundaRdbmsTestApplication)[5]"}]'

# 3. Run (will fail at post_comment since GITHUB_TOKEN is fake — that's OK)
PR_FLAKY_TESTS_DATA="$PR_DATA" \
KNOWN_FLAKY_TESTS="$KNOWN" \
PR_NUMBER=12345 \
GITHUB_TOKEN=fake \
GITHUB_REPOSITORY=camunda/camunda \
python3 .github/actions/detect-new-flaky-tests/detect_new_flaky_tests.py
```

The script will print all 3 steps with debug output and show whether each test
is KNOWN or NEW. It will fail at the `post_comment` step if a test is NEW
(since the token is fake), but the comparison output is still visible.

## FAQ

<details>
<summary><strong>Will old flaky tests be flagged as new after 60 days?</strong></summary>

No. The 60-day window is a rolling query against `main` and `stable/*`
branches. Tests on those branches keep running normally (via `push` and
`merge_group` triggers). As long as a test continues to flake on `main`, fresh
entries keep appearing in BigQuery — it never falls out of the window.

A test only drops out of the baseline if it **hasn't flaked on `main`/`stable/*`
for 60+ days**. That likely means it was fixed. If a PR triggers it again,
flagging it as NEW is the correct behavior.

</details>

<details>
<summary><strong>What about tests that flake very rarely (e.g., once every few months)?</strong></summary>

If a test last flaked on main 61+ days ago and your PR hits it, it will be
flagged as NEW. In this case:

1. Add the `ci:flaky-test-bypass` label to your PR
2. Create a `kind/flake` issue to track the test
3. Re-run CI — the gate will be skipped

</details>

<details>
<summary><strong>Does this gate run on main or stable branches?</strong></summary>

No. It only runs on `pull_request` events from non-fork repos. Test jobs on
`main` and `stable/*` continue to run normally and feed the BigQuery baseline.

</details>

<details>
<summary><strong>What happens if BigQuery is down or the query fails?</strong></summary>

The BigQuery query step will fail, which causes the `detect-new-flaky-tests`
job to fail. Since this job is in the `check-results` aggregation, it would
block merge. The `ci:flaky-test-bypass` label can be used to unblock.

</details>

<details>
<summary><strong>Can the gate produce false positives?</strong></summary>

The main source of false positives was mismatched test name formats between
Maven output and BigQuery. This is handled by boundary-aware matching (see
Step 3 above). Known cases:

- **Parameterized tests**: Maven reports `shouldDoX(Param)[5]`, BigQuery stores
  `shouldDoX(Param) variant` — both match the normalized key `shouldDoX`
- **Different methods sharing a prefix**: `shouldFind` will NOT match
  `shouldFindAllItems` thanks to the boundary check

If you encounter a false positive, use `ci:flaky-test-bypass` and report it to @monorepo-devops-team.

</details>

<details>
<summary><strong>Does the gate run on fork PRs?</strong></summary>

No. Fork PRs don't have access to repository secrets (Vault credentials),
which are required to query BigQuery for the baseline. GitHub enforces this
restriction for security — fork PRs cannot access secrets from the base repo.

This means fork PRs can potentially introduce new flaky tests without being
blocked. However:

- Fork PRs are rare in this repo
- Once a fork PR merges and the flaky test starts appearing on `main`, it will
  enter the BigQuery baseline and be tracked going forward
- Reviewers can still see test failures in the CI logs

A future improvement could use a `workflow_run` trigger to run the gate in the
base repo's context after the fork PR's CI completes. This would provide secret
access while maintaining security.

</details>

## File Structure

```
.github/actions/detect-new-flaky-tests/
├── action.yml                  # Composite action definition
├── detect_new_flaky_tests.py   # Detection logic (Python, stdlib only)
└── README.md                   # This file
```

