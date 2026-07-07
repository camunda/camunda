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
steps:
  - name: Collect cost-relevant workflow diffs (DataOps)
    shell: bash
    run: |
      set -uo pipefail
      mkdir -p /tmp/gh-aw/data
      cd "$GITHUB_WORKSPACE"

      # Cost-relevant keys. run:/jobs:/on: are intentionally EXCLUDED — they appear in
      # virtually every workflow diff, so including them (or matching context lines) makes
      # the pre-filter drop nothing and forces the agent to read every changed file.
      COST_RE='(runs-on|timeout-minutes|matrix|strategy|concurrency|paths|paths-ignore|services|container|continue-on-error|schedule|cron)'

      # 1. Candidate source files changed in the last 7 days (skip generated lock files —
      #    they are compiled from the source .md, so analysing them duplicates work).
      git log --since="7 days ago" --name-only --diff-filter=ACMR --pretty=format: \
        -- .github/workflows/ ':(exclude).github/workflows/*.lock.yml' \
        | sort -u | grep -v '^$' > /tmp/gh-aw/data/candidates.txt || true

      : > /tmp/gh-aw/data/diffs.txt
      : > /tmp/gh-aw/data/relevant.txt
      while IFS= read -r f; do
        [ -n "$f" ] || continue
        [ -f "$f" ] || continue
        # 2. Pre-filter on the CHANGED lines and hunk headers of a zero-context (-U0) diff.
        #    Matching only added/removed/hunk lines — not surrounding context — is what makes
        #    the filter actually discriminate between cost-relevant and cosmetic changes.
        diff="$(git log --since='7 days ago' -U0 -p --no-color -- "$f")"
        if ! printf '%s\n' "$diff" | grep -E '^[-+@]' | grep -Eq "$COST_RE"; then
          continue
        fi
        echo "$f" >> /tmp/gh-aw/data/relevant.txt
        {
          echo "===== FILE: $f ====="
          git log --since='7 days ago' --format='COMMIT %h %ad %s' --date=short -- "$f"
          echo "----- DIFF (zero context) -----"
          printf '%s\n' "$diff"
          echo
        } >> /tmp/gh-aw/data/diffs.txt
      done < /tmp/gh-aw/data/candidates.txt

      {
        echo "candidates=$(wc -l < /tmp/gh-aw/data/candidates.txt)"
        echo "cost_relevant_after_prefilter=$(wc -l < /tmp/gh-aw/data/relevant.txt)"
        echo "diffs_bytes=$(wc -c < /tmp/gh-aw/data/diffs.txt)"
      } > /tmp/gh-aw/data/summary.txt
      cat /tmp/gh-aw/data/summary.txt
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

1. **Read the pre-computed diffs**: A deterministic setup step (see the `steps:` block in the frontmatter) has already identified the candidate files, dropped generated `*.lock.yml` files, applied the cost-relevant pre-filter, and written the surviving diffs to disk. Do **not** re-run `git log`/`git diff` per file — that is what previously exhausted the token budget. Instead read:

   - `/tmp/gh-aw/data/summary.txt` — counts: total candidates, how many survived the pre-filter, and the diff payload size.
   - `/tmp/gh-aw/data/diffs.txt` — for each surviving file: a `===== FILE: <path> =====` header, its commits over the window (`COMMIT <hash> <date> <subject>`), and the zero-context diff. Read this file **once** and keep it in context; it is already compact.
   - `/tmp/gh-aw/data/relevant.txt` — the list of surviving file paths (one per line).

   The pre-filter keeps a file only when the **changed** lines (or hunk headers) of its diff mention a cost-relevant key (`runs-on`, `timeout-minutes`, `matrix`, `strategy`, `concurrency`, `paths`, `paths-ignore`, `services`, `container`, `continue-on-error`, `schedule`, `cron`). `run:`/`jobs:`/`on:` are deliberately excluded because they appear in almost every diff. This trades a small risk of missing an `env:`/`with:`/`uses:`-only cost change for staying well inside the effective-token budget. If a future change is suspected to slip through, broaden `COST_RE` in the setup step.

2. **Analyze each surviving file**: For every `===== FILE: =====` block in `/tmp/gh-aw/data/diffs.txt`, inspect its diff and look for the following cost-increasing patterns:

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
   - A summary section with the total number of CI changes and how many are cost-relevant (use the counts in `/tmp/gh-aw/data/summary.txt`)
   - A table of cost-impacting changes with columns: File, Change Type, Impact Level, Description including link to commit or Pull Request. Include at most **15 rows**, ranked by Impact Level (High → Medium → Low). Keep each Description cell under **200 characters**. If more than 15 cost-impacting changes were detected, note the omitted count in the summary.
   - Up to **5 recommendations** for cost optimization where applicable, one short paragraph each. Only include recommendations that would actually **save money** (e.g. shrink a paid runner, remove a redundant paid-runner job, tighten path filters on a paid-runner workflow, add concurrency cancellation). Do **not** include speculative future-watch items, "monitor X", "audit for potential future migration", or anything that does not reduce spend today. If there are no money-saving recommendations, omit the section entirely rather than padding it.
   - Address the report to the CI DRI by mentioning `@camunda/monorepo-ci-dri` in the report body (e.g. in the summary or a short intro line) so they are notified.
   - Do NOT inline diff snippets, `<details>` blocks, or per-change breakdowns — link to the commit or PR instead.

5. **Upsert persistent issue**:
   - Search for an existing issue with exact title `Weekly CI Change Cost Impact Analysis`
   - If it exists, replace its body with the newly generated report body using `update-issue`
   - If it does not exist, create it once with `create-issue` using that exact title, then ensure its body is the generated report
   - Ensure labels `area/build` and `component/build-pipeline` are present

## Output Format

Use GitHub-flavored markdown. Start nested headings at `###`. Keep the body terse — link to commits or PRs instead of quoting diffs. If no cost-impacting changes are found, the persistent issue body should clearly state that no cost-relevant CI changes were detected this week.
