# Post CI Failure Reasons

A composite GitHub Action that collects job annotations from unsuccessful GitHub Actions jobs in the current Unified CI workflow run and posts them as a self-updating PR comment.

## Purpose

This action helps developers quickly understand CI failures at a glance by:

- Collecting all annotations (errors, warnings, notices) from failed or cancelled jobs
- Formatting them in a readable markdown format
- Posting them as a comment on pull requests (automatically on `pull_request` events)
- Automatically updating the comment on subsequent runs to avoid clutter (and deleting it on pass)

The goal is to improve transparency and shorten the feedback cycle!

## Inputs

|     Input      |                                                     Description                                                     | Required |  Default  |
|----------------|---------------------------------------------------------------------------------------------------------------------|----------|-----------|
| `github_token` | GitHub token for API access. Needs `actions:read`, `checks:read` and `pull-requests:write` permissions.             | Yes      | -         |
| `has_failures` | Whether there are any failures or cancellations in the workflow. When `'false'`, deletes outdated failure comments. | No       | `'false'` |

## Usage

### Basic Usage (in a Unified CI `check-results` job)

```yaml
jobs:
  check-results:
    if: always()
    runs-on: ubuntu-latest
    timeout-minutes: 3
    permissions:
      actions: read
      checks: read
      pull-requests: write
    needs: [job1, job2, job3]  # All jobs to check
    steps:
      - uses: actions/checkout@v6

      - name: Collect failure annotations or clean up on success
        continue-on-error: true
        uses: ./.github/actions/post-ci-failure-reasons
        with:
          github_token: ${{ github.token }}
          has_failures: ${{ contains(needs.*.result, 'cancelled') || contains(needs.*.result, 'failure') }}

      - name: Fail if any job failed
        run: |
          exit ${{ ((contains(needs.*.result, 'cancelled') || contains(needs.*.result, 'failure')) && 1) || 0 }}
```

## Features

- **Automatic comment updates**: Uses a hidden HTML marker to identify and update existing comments instead of creating duplicates
- **Automatic cleanup on success**: Deletes outdated failure comments when all checks pass, keeping PRs clean
- **Cross-event support**: Works on pull requests, merge queue, push to `main` branch, etc. (PR comments only on GH `pull_request` events)
- **Graceful degradation**: Won't fail the workflow if it encounters errors
- **Rich formatting**: Includes job names, status, links, and structured annotation display

## Output Format

The action generates a markdown file with the following structure:

```markdown
<!-- ci-failure-analysis -->
## 🔍 CI Failure Analysis

GHA workflow run: [#123](https://github.com/org/repo/actions/runs/456)

Found **2** unsuccessful job(s)

### 📋 GHA job: `test-job` (failure)
🔗 [View job logs](https://github.com/org/repo/actions/runs/456/job/789)

Found 3 GHA annotation(s):

- **ERROR**: Test failed: expected 5 but got 3
- **WARNING**: Deprecated API usage detected
- **NOTICE**: Code coverage below threshold

---

_Last updated: 2026-01-30 12:34:56 UTC_
```

## Requirements

- GitHub CLI (`gh`) must be available in the runner
- `jq` must be available in the runner

Both are pre-installed on GitHub-hosted runners.

## Permissions

The action requires the following token permissions:

- `actions: read` - To query workflow run and jobs data
- `checks: read` - To read check run annotations
- `pull-requests: write` - To post/update PR comments

