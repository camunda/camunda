---

name: ci-runner-utilization
description: Detect CI runner underutilization and recommend downsizing for cost savings. Use when asked about CI costs, runner sizing, resource waste, underutilization, or right-sizing self-hosted runners.

---

# CI Runner Utilization & Downsizing Analysis

Analyzes CPU and memory utilization of self-hosted CI runners in the `camunda/camunda` repository to find overprovisioned jobs and recommend cheaper runner types.

## What costs money and what doesn't

**Self-hosted runners cost money** — these are Kubernetes pods on GCP or AWS billed by core-hour. Their `runner_type` starts with `gcp-` (e.g., `gcp-perf-core-16-default`) or `aws-`. More cores = higher cost. Downsizing from 16 to 8 cores roughly halves the per-job compute cost.

**GitHub-hosted runners are free for public repos** — jobs on `ubuntu-latest` / `ubuntu-slim` have `runner_type = NULL` in BigQuery. Ignore them entirely for cost optimization.

**CPU is the expensive resource.** Memory is proportional to cores and much cheaper per unit. Focus downsizing decisions on CPU utilization; only check memory to ensure a smaller runner won't OOM.

**Perf runners cost more than standard runners** — `gcp-perf-core-N` uses faster CPUs than `gcp-core-N`. Only suggest downgrading perf→standard if the job doesn't need fast CPUs (e.g., linting, static analysis, artifact uploads).

**Longrunning runners cost more than default** — `-longrunning` has higher durability guarantees and costs more. Only needed for jobs that genuinely run long or are release-critical.

## Runner type naming convention

Format: `{cloud}-{tier?}-core-{cores}-{durability}`

| Component  |                                     Values                                     |
|------------|--------------------------------------------------------------------------------|
| cloud      | `gcp`, `aws`                                                                   |
| tier       | `perf` (fast CPU, more expensive) or absent (standard)                         |
| cores      | `2`, `4`, `8`, `16` — number of vCPUs                                          |
| durability | `default` (cheap, preemptible), `release` / `longrunning` (expensive, durable) |

Available self-hosted runner types can be found on https://github.com/camunda/infra-global-github-actions/blob/main/actionlint/actionlint.yaml

Downsizing follows the same family: `gcp-perf-core-16-default` → `gcp-perf-core-8-default`.

## Prerequisites

- `bq` CLI authenticated with access to project `ci-30-162810`
  - Verify: `bq query --use_legacy_sql=false 'SELECT 1'`
- Data is in `ci-30-162810.prod_ci_analytics.build_status_v2` (90-day retention)
- CPU/memory metrics were added on 2026-05-18 — data availability starts from that date

## How to analyze

### Step 1: Identify underutilized self-hosted jobs

This query finds jobs where the peak CPU p95 never exceeds 50% of the runner's capacity, grouped by runner type. Only self-hosted runners (non-NULL `runner_type`) are included.

```bash
bq query --use_legacy_sql=false --format=prettyjson '
SELECT
  job_name,
  runner_type,
  COUNT(*) AS samples,
  ROUND(AVG(cpu_usage_ratio_p95), 3) AS avg_cpu_p95,
  ROUND(MAX(cpu_usage_ratio_p95), 3) AS max_cpu_p95,
  ROUND(AVG(memory_usage_ratio_p95), 3) AS avg_mem_p95,
  ROUND(MAX(memory_usage_ratio_p95), 3) AS max_mem_p95
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE cpu_usage_ratio_p95 IS NOT NULL
  AND ci_url LIKE "%camunda/camunda%"
  AND runner_type IS NOT NULL
  AND (runner_type LIKE "gcp-%" OR runner_type LIKE "aws-%")
GROUP BY job_name, runner_type
HAVING MAX(cpu_usage_ratio_p95) <= 0.5
ORDER BY max_cpu_p95 ASC
'
```

### Step 2: Get full utilization picture (all self-hosted jobs)

This shows all jobs sorted by CPU usage so you can see the full spectrum and identify the boundary between "needs downsizing" and "correctly sized":

```bash
bq query --use_legacy_sql=false --format=csv --max_rows=200 '
SELECT
  job_name,
  runner_type,
  COUNT(*) AS samples,
  ROUND(AVG(cpu_usage_ratio_p95), 3) AS avg_cpu_p95,
  ROUND(MAX(cpu_usage_ratio_p95), 3) AS max_cpu_p95,
  ROUND(AVG(memory_usage_ratio_p95), 3) AS avg_mem_p95,
  ROUND(MAX(memory_usage_ratio_p95), 3) AS max_mem_p95
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE cpu_usage_ratio_p95 IS NOT NULL
  AND ci_url LIKE "%camunda/camunda%"
  AND runner_type IS NOT NULL
  AND (runner_type LIKE "gcp-%" OR runner_type LIKE "aws-%")
GROUP BY job_name, runner_type
ORDER BY max_cpu_p95 ASC
'
```

### Step 3: Check runner type distribution

Understand which runner types carry the most jobs and runs:

```bash
bq query --use_legacy_sql=false --format=prettyjson '
SELECT
  runner_type,
  COUNT(DISTINCT job_name) AS distinct_jobs,
  COUNT(*) AS total_runs,
  ROUND(AVG(cpu_usage_ratio_p95), 3) AS overall_avg_cpu_p95
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE ci_url LIKE "%camunda/camunda%"
  AND cpu_usage_ratio_p95 IS NOT NULL
  AND runner_type IS NOT NULL
GROUP BY runner_type
ORDER BY total_runs DESC
'
```

### Step 4: Deep-dive a specific job (time series)

When you want to see if a job's usage is stable or has spikes over time:

```bash
bq query --use_legacy_sql=false --format=prettyjson '
SELECT
  report_time,
  job_name,
  runner_type,
  ROUND(cpu_usage_ratio_p95, 3) AS cpu_p95,
  ROUND(memory_usage_ratio_p95, 3) AS mem_p95,
  build_status
FROM `ci-30-162810.prod_ci_analytics.build_status_v2`
WHERE ci_url LIKE "%camunda/camunda%"
  AND job_name = "REPLACE_WITH_JOB_NAME"
  AND cpu_usage_ratio_p95 IS NOT NULL
ORDER BY report_time DESC
LIMIT 50
'
```

## How to interpret results and make recommendations

### Utilization metrics

- `cpu_usage_ratio_p95`: 95th-percentile CPU usage as a fraction of the container's CPU limit (0.0–1.0). A value of 0.25 on a 16-core runner means the job used ~4 cores at p95.
- `memory_usage_ratio_p95`: Same for memory. Check this to ensure a smaller runner won't OOM.
- Always use `MAX(cpu_usage_ratio_p95)` across runs, not just the average — you need to handle the worst case, not the typical case.

### Decision framework

| Max CPU p95 |                    Action                    | Confidence |
|-------------|----------------------------------------------|------------|
| ≤ 25%       | Downsize by 4x (16→4 cores) or 2x (8→4, 4→2) | High       |
| 25–50%      | Downsize by 2x (16→8, 8→4)                   | High       |
| 50–65%      | Borderline — downsize only with ≥50 samples  | Medium     |
| 65–80%      | Keep current size                            | —          |
| 80–100%     | Correctly sized or consider upsizing         | —          |

### Memory safety check

Before recommending a downsize, verify `max_mem_p95`:
- If `max_mem_p95 < 0.5` on the current runner, halving cores (and thus memory) is safe.
- If `max_mem_p95 > 0.5`, halving would risk OOM. Consider keeping the larger runner or only stepping down one size (16→8 instead of 16→4).

If no suitable runner type can be found, suggest creating new runner types.

### Sample count matters

- **≥ 100 samples**: High confidence — safe to act on.
- **30–100 samples**: Medium confidence — recommend with a note to monitor.
- **< 30 samples**: Low confidence — flag for future review, don't act yet.

### Forming the recommendation

For each underutilized job:
1. Note the current `runner_type` and extract the core count.
2. Multiply `max_cpu_p95` by the core count to get effective cores used.
3. Find the smallest available runner type (same family) that provides ≥1.5x the effective cores.
4. Check memory won't OOM on the smaller runner.
5. State: job name, current runner, suggested runner, CPU headroom, memory headroom, sample count.

Example: A job with `max_cpu_p95 = 0.25` on `gcp-perf-core-8-default` uses ~2 effective cores.
A `gcp-perf-core-4-default` (4 cores) gives 2x headroom → recommend it.

If no suitable runner type can be found, suggest creating new runner types.

### Implementing the recommendation

For each underutilized job:
1. Ask the user for confirmation to apply the recommendation.
2. Find the GitHub Action workflow YAML file that contains the job, and adjust the `runs-on:` label.
3. Offer to commit and push the changes to a Pull Request, and observe the CI runtime behavior on that PR.
4. Confirm job run times on the PR do not increase meaningfully.

## BigQuery table schema reference

Table: `ci-30-162810.prod_ci_analytics.build_status_v2` (90-day retention)

|            Column             |   Type    |                    Description                    |
|-------------------------------|-----------|---------------------------------------------------|
| `report_time`                 | TIMESTAMP | When the row was submitted                        |
| `ci_url`                      | STRING    | `https://github.com/{owner}/{repo}`               |
| `workflow_name`               | STRING    | GitHub Actions workflow name                      |
| `job_name`                    | STRING    | Job identifier                                    |
| `build_id`                    | STRING    | `{run_id}/{attempt}`                              |
| `build_trigger`               | STRING    | Event name (push, pull_request, schedule, etc.)   |
| `build_status`                | STRING    | success, failed, cancelled                        |
| `build_ref`                   | STRING    | Git ref                                           |
| `build_base_ref`              | STRING    | Target branch (PRs/merge queue)                   |
| `build_head_ref`              | STRING    | Source branch (PRs)                               |
| `build_duration_milliseconds` | INTEGER   | Job duration                                      |
| `runner_name`                 | STRING    | Runner hostname                                   |
| `runner_arch`                 | STRING    | CPU architecture (x86_64, aarch64)                |
| `runner_os`                   | STRING    | OS (linux, windows)                               |
| `runner_type`                 | STRING    | Self-hosted runner label (NULL for GitHub-hosted) |
| `cpu_usage_ratio_avg`         | FLOAT64   | Average CPU utilization (0.0–1.0)                 |
| `cpu_usage_ratio_p95`         | FLOAT64   | 95th percentile CPU utilization                   |
| `memory_usage_ratio_avg`      | FLOAT64   | Average memory utilization (0.0–1.0)              |
| `memory_usage_ratio_p95`      | FLOAT64   | 95th percentile memory utilization                |
| `user_reason`                 | STRING    | User-provided failure reason                      |
| `user_description`            | STRING    | User-provided details                             |

## Data collection pipeline

1. `start-build-monitor` action starts a background monitor (5s polling) collecting CPU/memory from cgroups v2/v1 or `/proc/`
2. `submit-build-status` action stops the monitor, aggregates stats (avg, p95) via AWK, reads `runner_type` from `/home/runner/.camunda-arc-runner-info/runs-on`, and POSTs to BigQuery
3. Metrics are normalized ratios relative to the container's CPU/memory limits

Source: `camunda/infra-global-github-actions/start-build-monitor/` and `camunda/infra-global-github-actions/submit-build-status/`
