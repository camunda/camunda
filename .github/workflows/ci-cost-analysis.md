---
emoji: 💰
name: CI Change Cost Impact Analysis
description: Weekly report analyzing recent CI changes for potential cost increases
on:
  schedule: weekly on monday
  workflow_dispatch:
checkout:
  fetch-depth: 0
permissions:
  contents: read
  actions: read
  issues: read
  pull-requests: read
tools:
  github:
    mode: gh-proxy
    toolsets: [default]
safe-outputs:
  create-issue:
    max: 1
    labels: [area/build, component/build-pipeline]
  update-issue:
    max: 1
    target: "*"
---

# CI Change Cost Impact Analysis

## Task

You are a CI/CD cost analyst. Analyze all changes made to GitHub Actions workflow files (`.github/workflows/`) in this repository during the **last 7 days** and identify changes that could potentially **increase CI costs**.

**Cost model for this repo**: `camunda/camunda` is a public repository, so standard GitHub-hosted runners are free — including single-CPU variants like `ubuntu-slim` (see [GitHub's docs](https://docs.github.com/en/enterprise-cloud@latest/actions/reference/runners/github-hosted-runners#single-cpu-runners)) and the regular labels `ubuntu-latest`, `ubuntu-24.04`, `windows-latest`, `macos-latest`. Cost is incurred on **self-hosted runners** (labels like `gcp-*`, `aws-*`). Note: GitHub-hosted *larger/premium* runners (e.g., `*-16-core`, `macos-large`, GPU runners) can still be billed and should be treated as cost-relevant if they appear.

Use exactly one persistent issue for reporting:
- **Persistent issue title**: `Weekly CI Change Cost Impact Analysis`
- Do not create rotating weekly issues.
- Overwrite the persistent issue body with the latest report each run.

## Steps

1. **Identify changed workflow files**: Use `git log --since="7 days ago" --name-only --diff-filter=ACMR -- .github/workflows/` on the `main` branch to find all workflow files that were added, changed, or modified in the last week.

2. **Analyze each changed file**: For every changed workflow file, use `git diff HEAD~...HEAD -- <file>` (or the appropriate range covering the last 7 days) to inspect the actual diffs. Look for the following cost-increasing patterns:

   ### Cost-Increasing Patterns to Detect

   Only flag a pattern when the affected job runs on a **paid runner**, meaning either a self-hosted runner (`runs-on: gcp-*`, `aws-*`, or any non-GitHub-provided label) or a larger/premium GitHub-hosted runner (labels with a core-count, GPU, or Arm64 suffix, e.g. `ubuntu-latest-16-core`). Skip patterns that exclusively affect standard free GitHub-hosted runners (`ubuntu-latest`, `ubuntu-slim`, `ubuntu-24.04`, `windows-latest`, `macos-latest`, etc.).

   - **New workflow files**: Brand new workflow files added with paid-runner jobs
   - **New jobs added**: Entirely new job definitions added to existing workflows, running on paid runners
   - **New triggers added**: Additional trigger events on workflows with paid-runner jobs (e.g., adding `push` alongside `pull_request`, adding `schedule`, adding more branch patterns)
   - **Increased timeouts**: `timeout-minutes` values being raised on paid-runner jobs
   - **Larger / shifted runner types**: Changes that move a job to a larger paid runner, or migrate a job from a free runner to a paid one (e.g., `ubuntu-latest` → `gcp-core-8-default`, or `ubuntu-latest` → `ubuntu-latest-16-core`)
   - **Extended matrix strategies**: More entries in matrix configurations on paid-runner jobs
   - **New or expanded test suites**: Addition of large test commands, new test frameworks, or expansion of test scope on paid-runner jobs
   - **Removed concurrency controls**: Removal of `concurrency` groups that previously prevented duplicate paid-runner runs
   - **Removed path filters**: Removing `paths:` or `paths-ignore:` filters causing workflows with paid-runner jobs to trigger more broadly
   - **Added retry logic**: Adding retry steps or `continue-on-error` that multiply execution time on paid-runner jobs
   - **Container or service additions**: New `services:` or `container:` definitions on paid-runner jobs that add overhead

3. **Assess impact**: For each detected change, estimate the cost impact as **Low**, **Medium**, or **High** based on:
   - How frequently the workflow runs (scheduled vs. per-push vs. per-PR)
   - The size of the paid-runner change (e.g., `gcp-core-2` → `gcp-core-16` is High; minor increase is Medium)
   - The number of additional paid-runner minutes per run

   Changes that only affect standard free GitHub-hosted runners have zero cost impact and must not appear in the report.

4. **Generate report body**. The body must fit under the 10 KB `update-issue` safe-output limit; the structural caps below keep it well under that budget without measuring bytes:
   - A summary section with the total number of CI changes and how many are cost-relevant
   - A table of cost-impacting changes with columns: File, Change Type, Impact Level, Description including link to commit or Pull Request. Include at most **15 rows**, ranked by Impact Level (High → Medium → Low). Keep each Description cell under **200 characters**. If more than 15 cost-impacting changes were detected, note the omitted count in the summary.
   - Up to **5 recommendations** for cost optimization where applicable, one short paragraph each.
   - Do NOT inline diff snippets, `<details>` blocks, or per-change breakdowns — link to the commit or PR instead.

5. **Upsert persistent issue**:
   - Search for an existing issue with exact title `Weekly CI Change Cost Impact Analysis`
   - If it exists, replace its body with the newly generated report body using `update-issue`
   - If it does not exist, create it once with `create-issue` using that exact title, then ensure its body is the generated report
   - Ensure labels `area/build` and `component/build-pipeline` are present

## Output Format

Use GitHub-flavored markdown. Start nested headings at `###`. Keep the body terse — link to commits or PRs instead of quoting diffs. If no cost-impacting changes are found, the persistent issue body should clearly state that no cost-relevant CI changes were detected this week.
