---

name: ci-push-workflow-health
description: Analyze CI failure patterns for push-triggered workflow jobs on main and stable/* branches. Use when asked about CI health, broken jobs, flaky workflows, failure rates, or push-trigger problems.

---

# CI Push Workflow Health Analysis

Analyzes failure patterns for `push`-triggered GitHub Actions workflow runs on `main` and `stable/*` branches using BigQuery CI Analytics data.

## Prerequisites

- `bq` CLI available and authenticated (`bq show ci-30-162810:prod_ci_analytics.build_status_v2` should work)
- BigQuery table: `ci-30-162810.prod_ci_analytics.build_status_v2`

## Analysis approach

Run the following queries in sequence. Each builds on the previous to drill from broad summary down to specific broken jobs. Adjust `INTERVAL 14 DAY` if a shorter or longer window is needed. Longer windows are costlier (maximum 90 days) and less impacted by recent trends/changes.

---

### Step 1 — Overview: total runs and failure counts per branch

Establishes the scope and identifies which branches have significant failure volume.

```sql
SELECT
  build_ref,
  COUNT(*) AS total,
  COUNTIF(build_status != 'success') AS failures,
  MIN(report_time) AS earliest,
  MAX(report_time) AS latest
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE
  ci_url = 'https://github.com/camunda/camunda'
  AND build_trigger = 'push'
  AND (
    build_ref = 'refs/heads/main'
    OR build_ref LIKE 'refs/heads/stable/%'
  )
  AND report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
GROUP BY build_ref
ORDER BY build_ref
```

---

### Step 2 — Workflow breakdown: failure rate per workflow per branch

Identifies which workflows are the problem areas. Key signals:
- `failure_pct` > 10% on a high-volume workflow = high impact
- `failure_pct` = 100% = completely broken
- High `cancellations` relative to `failures` = concurrency issue (not real test failures) or timeouts (they are a problem)

```sql
SELECT
  build_ref,
  workflow_name,
  COUNT(*) AS total_runs,
  COUNTIF(build_status != 'success') AS failures,
  ROUND(100.0 * COUNTIF(build_status != 'success') / COUNT(*), 1) AS failure_pct,
  COUNTIF(build_status = 'failure') AS hard_failures,
  COUNTIF(build_status = 'cancelled') AS cancellations,
  COUNTIF(build_status = 'aborted') AS aborts
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE
  ci_url = 'https://github.com/camunda/camunda'
  AND build_trigger = 'push'
  AND (
    build_ref = 'refs/heads/main'
    OR build_ref LIKE 'refs/heads/stable/%'
  )
  AND report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
GROUP BY build_ref, workflow_name
HAVING failures > 5
ORDER BY build_ref, failures DESC
```

---

### Step 3 — Job-level failures in high-failure-rate workflows (excluding Unified CI)

Drills into specific jobs that are broken. Focus on workflows identified as problematic in step 2. A job at 100% failure rate over many runs is permanently broken (not flaky).

```sql
SELECT
  build_ref,
  workflow_name,
  job_name,
  COUNT(*) AS total_runs,
  COUNTIF(build_status != 'success') AS failures,
  ROUND(100.0 * COUNTIF(build_status != 'success') / COUNT(*), 1) AS failure_pct,
  COUNTIF(build_status = 'failure') AS hard_failures,
  COUNTIF(build_status = 'cancelled') AS cancellations
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE
  ci_url = 'https://github.com/camunda/camunda'
  AND build_trigger = 'push'
  AND (
    build_ref = 'refs/heads/main'
    OR build_ref LIKE 'refs/heads/stable/%'
  )
  AND report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
  AND build_status != 'success'
  AND workflow_name NOT IN ('CI', 'Check Licenses')
GROUP BY build_ref, workflow_name, job_name
HAVING failures >= 5
ORDER BY build_ref, failures DESC
LIMIT 80
```

---

### Step 4 — Job-level failures within the Unified CI workflow

The `CI` workflow runs on nearly every push so its absolute failure count is high even at low failure rates. It is the required status check so impact is high. Look for jobs at with high failure rate — those are specific test areas that are broken, not representative of the overall CI health.

```sql
SELECT
  build_ref,
  job_name,
  COUNT(*) AS total_runs,
  COUNTIF(build_status != 'success') AS failures,
  ROUND(100.0 * COUNTIF(build_status != 'success') / COUNT(*), 1) AS failure_pct,
  COUNTIF(build_status = 'failure') AS hard_failures,
  COUNTIF(build_status = 'cancelled') AS cancellations,
  ROUND(AVG(build_duration_milliseconds) / 60000, 1) AS avg_duration_min
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE
  ci_url = 'https://github.com/camunda/camunda'
  AND build_trigger = 'push'
  AND (
    build_ref = 'refs/heads/main'
    OR build_ref LIKE 'refs/heads/stable/%'
  )
  AND report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
  AND workflow_name = 'CI'
  AND build_status != 'success'
GROUP BY build_ref, job_name
HAVING failures >= 3
ORDER BY build_ref, failures DESC
LIMIT 60
```

---

### Step 5 — Weekly trend on main

Shows whether the overall failure rate is improving or degrading. A spike in one week can be traced back to a bad commit window.

```sql
SELECT
  DATE_TRUNC(DATE(report_time), WEEK) AS week,
  COUNT(*) AS total_runs,
  COUNTIF(build_status != 'success') AS failures,
  ROUND(100.0 * COUNTIF(build_status != 'success') / COUNT(*), 2) AS failure_pct
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE
  ci_url = 'https://github.com/camunda/camunda'
  AND build_trigger = 'push'
  AND build_ref = 'refs/heads/main'
  AND report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
GROUP BY week
ORDER BY week
```

---

### Step 6 — Daily trend for specific suspect workflows

Use this to check whether a workflow's failures are chronic (broken since day 1) or a recent regression. Replace the `workflow_name` list with the workflows identified as problematic in step 2.

```sql
SELECT
  build_ref,
  workflow_name,
  DATE(report_time) AS day,
  COUNT(*) AS total_runs,
  COUNTIF(build_status != 'success') AS failures,
  ROUND(100.0 * COUNTIF(build_status != 'success') / COUNT(*), 0) AS failure_pct
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE
  ci_url = 'https://github.com/camunda/camunda'
  AND build_trigger = 'push'
  AND build_ref IN ('refs/heads/main', 'refs/heads/stable/8.7', 'refs/heads/stable/8.8', 'refs/heads/stable/8.9')
  AND report_time >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
  AND workflow_name IN (
    '[Legacy] Operate / E2E Tests',
    '[Legacy] Tasklist / E2E Tests',
    '[Legacy] Tasklist',
    '[Legacy] Operate'
    -- add other suspect workflows here
  )
GROUP BY build_ref, workflow_name, day
HAVING total_runs > 0
ORDER BY build_ref, workflow_name, day
```

---

### Step 7 — Per-commit check status via GitHub Checks API (optional, high GitHub API quota cost)

> **WARNING — High GitHub API quota usage.** This step uses the GitHub Checks API to fetch check suites and check runs for every commit in the window. Each commit can consume 10–50+ API requests depending on the number of check suites. A 3-hour window on `main` can easily use 500–2000+ requests against a PAT's 5000/hour limit. **Always ask the user for confirmation before running this step**, and keep the `--hours` window as small as possible (3–6 hours recommended, never more than 24).

This step uses the `main_branch_health.py` script (bundled in this scripts subfolder) to query the GitHub Checks API directly. Unlike the BigQuery-based steps above, this provides the **exact same check status view as GitHub's UI** on each commit — including check suite names, individual check run conclusions, and per-commit pass/fail summaries. Use this when you need to:

- Verify exactly which checks failed on specific recent commits (matching what the user sees on GitHub)
- Cross-reference BigQuery aggregate data with actual commit-level check results
- Debug check runs that may not appear in BigQuery (e.g., third-party checks, non-workflow checks)

#### Prerequisites

- Python 3.9+ with `requests` installed (`pip install requests`)
- `GITHUB_TOKEN` or `GH_TOKEN` environment variable set with a PAT or fine-grained token that has repo read access

#### Before running — confirm with the user

**You MUST ask the user for confirmation before executing this script.** Present them with:
1. The branch and time window you plan to query
2. A warning that this will consume significant GitHub API quota
3. Ask if they want to proceed

Example prompt: _"I'd like to run the commit-level checks script for `main` over the last 6 hours. This will use the GitHub Checks API which consumes significant API quota (potentially hundreds of requests). Should I proceed?"_

#### Usage

```bash
# Table output (default) — last 6 hours on main
python .claude/skills/ci-push-workflow-health/scripts/main_branch_health.py \
  --branch main --hours 6 --output table --fallback-to-statuses

# JSON output for programmatic analysis
python .claude/skills/ci-push-workflow-health/scripts/main_branch_health.py \
  --branch main --hours 6 --output json --fallback-to-statuses

# Stable branch, minimal window
python .claude/skills/ci-push-workflow-health/scripts/main_branch_health.py \
  --branch stable/8.8 --hours 3 --output table --fallback-to-statuses
```

#### Key flags

|           Flag           |                         Description                         |
|--------------------------|-------------------------------------------------------------|
| `--hours N`              | Look back N hours (default 3). **Keep this small** (1–3).   |
| `--branch NAME`          | Branch to scan (default `main`). Use `stable/8.8` etc.      |
| `--output table\|json`   | Output format. Use `json` when you need to process results. |
| `--fallback-to-statuses` | Fall back to commit status API if no check runs found.      |
| `--workers N`            | Parallel workers (default 8).                               |

#### Output

The table output shows per-commit health:

```
sha      committed_at              summary  overall  source  failed_jobs
-------  ------------------------  -------  -------  ------  -----------
abc1234  2025-05-26T10:30:00+00:00  45/47   failure  checks  CI / java-checks (failure), ...
def5678  2025-05-26T10:15:00+00:00  47/47   success  checks  -
```

Followed by aggregated failed job totals across all commits in the window.

---

## How to run queries

```bash
bq query --use_legacy_sql=false --format=pretty '<SQL>'
```

## How to interpret results

|                  Signal                   |                                         Interpretation                                         |
|-------------------------------------------|------------------------------------------------------------------------------------------------|
| `failure_pct` = 100% over many runs       | Permanently broken — needs immediate fix or disable                                            |
| `failure_pct` = 100% over few runs        | Could be flaky or a recent commit broke it — check trend                                       |
| High `cancellations`, low `hard_failures` | Concurrency problem ( workflow triggered too often, cancels in-flight runs) or timeout problem |
| `failure_pct` improving in recent weeks   | Fix landed on main but may not be backported to stable                                         |

## Report structure

Summarize findings in this order:

1. **Overview table** — failure % per branch
2. **Critical: permanently broken jobs** — 100% failure rate, high run count
3. **High-impact problems by area** — grouped by root cause pattern
4. **Trend** — improving, degrading, or stable
5. **Recommendations** — concrete next steps (backport, disable, add concurrency guard, etc.)

