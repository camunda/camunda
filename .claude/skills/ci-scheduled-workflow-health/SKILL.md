---

name: ci-scheduled-workflow-health
description: Generate an HTML health report for all scheduled GitHub Actions workflows in this monorepo. Discovers workflows with `schedule:` triggers from the `main` branch via git, queries the GitHub API for recent run results, extracts `# owner:` metadata from YAML comments, and produces a categorized report (failing, flaky, ok, no runs). Use when asked about CI health, scheduled workflow health, nightly build failures, flaky CI, or workflow ownership.

---

# Scheduled Workflow Health Report

Generates an HTML report showing the health status of all scheduled GitHub Actions workflows in the `camunda/camunda` repository.

Then summarizes the concerning failing and flaky workflows by owner.

## What it does

1. Lists all workflow files from `git show main:.github/workflows/`
2. Filters to those containing a `schedule:` trigger
3. Extracts the `# owner:` comment from the first 20 lines of each YAML file
4. Queries the GitHub API (via `gh`) for the last 5 scheduled runs on `main`
5. Classifies each workflow:
   - **failing** — most recent run failed AND all checked runs failed
   - **flaky** — most recent run failed BUT some checked runs passed
   - **ok** — most recent run passed
   - **no_runs** / **in_progress** — no completed scheduled runs found
6. Outputs `scheduled-workflow-report.html` with collapsible sections, owner info, and a legend

## Prerequisites

- `git` with access to the `main` branch (fetched)
- `gh` CLI authenticated (`gh auth status`)
- `python3`

## How to run

```bash
python3 .claude/skills/ci-scheduled-workflow-health/scripts/scheduled-workflow-report.py
```

The script is at: `.claude/skills/ci-scheduled-workflow-health/scripts/scheduled-workflow-report.py`.

Output: `scheduled-workflow-report.html` in the current directory. Open with a browser.

stderr shows progress and a summary of failing/flaky workflows with their owners.

## Customization

- `RUNS_TO_CHECK` constant (default 5) controls how many recent runs to inspect per workflow
- `OWNER` / `REPO` constants control the target repository
- Owner extraction looks for `# owner: <team>` in the first 20 lines of each workflow YAML

## Key design decisions

- Uses `git show main:` instead of filesystem reads to always reflect the `main` branch state
- Uses `gh api` (no pagination) since we only need ≤5 runs per workflow
- Filters API calls to `event=schedule&branch=main` to only see scheduled runs
- HTML report uses `<details>` for collapsible sections — failing/flaky are open by default

