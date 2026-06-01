---
emoji: 💰
name: CI Cost Impact Analysis
description: Weekly report analyzing recent CI changes for potential cost increases
on:
  schedule: weekly on monday
  skip-if-match: 'is:issue is:open in:title "[ci-cost-analysis] "'
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
    title-prefix: "[ci-cost-analysis] "
    labels: [ci, cost-analysis]
    expires: 14
    close-older-issues: true
---

# CI Cost Impact Analysis

## Task

You are a CI/CD cost analyst. Analyze all changes made to GitHub Actions workflow files (`.github/workflows/`) in this repository during the **last 7 days** and identify changes that could potentially **increase CI costs**.

## Steps

1. **Identify changed workflow files**: Use `git log --since="7 days ago" --name-only --diff-filter=ACMR -- .github/workflows/` to find all workflow files that were added, changed, or modified in the last week.

2. **Analyze each changed file**: For every changed workflow file, use `git diff HEAD~...HEAD -- <file>` (or the appropriate range covering the last 7 days) to inspect the actual diffs. Look for the following cost-increasing patterns:

   ### Cost-Increasing Patterns to Detect

   - **Increased timeouts**: `timeout-minutes` values being raised
   - **Larger runner types**: Changes from smaller to larger runners (e.g., `ubuntu-latest` → `ubuntu-latest-16-core`, or any move to larger/paid runners)
   - **New jobs added**: Entirely new job definitions added to existing workflows
   - **New workflow files**: Brand new workflow files added
   - **New triggers added**: Additional trigger events (e.g., adding `push` alongside `pull_request`, adding `schedule`, adding more branch patterns)
   - **Extended matrix strategies**: More entries in matrix configurations
   - **New or expanded test suites**: Addition of large test commands, new test frameworks, or expansion of test scope
   - **Removed concurrency controls**: Removal of `concurrency` groups that previously prevented duplicate runs
   - **Removed path filters**: Removing `paths:` or `paths-ignore:` filters causing workflows to trigger more broadly
   - **Added retry logic**: Adding retry steps or `continue-on-error` that multiply execution time
   - **Container or service additions**: New `services:` or `container:` definitions that add overhead

3. **Assess impact**: For each detected change, estimate the cost impact as **Low**, **Medium**, or **High** based on:
   - How frequently the workflow runs (scheduled vs. per-push vs. per-PR)
   - The size of the runner change
   - The number of additional minutes per run

4. **Generate report**: Create a well-structured issue with:
   - A summary section with the total number of CI changes and how many are cost-relevant
   - A table of cost-impacting changes with columns: File, Change Type, Impact Level, Description
   - A detailed breakdown per change with the relevant diff snippets in collapsible sections
   - Recommendations for cost optimization where applicable

## Output Format

Use GitHub-flavored markdown. Start nested headings at `###`. Use `<details>` and `<summary>` tags for collapsible diff sections. If no cost-impacting changes are found, create an issue stating that no cost-relevant CI changes were detected this week.
