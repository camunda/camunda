# Outdated PR Detection Scripts

Automated detection and labeling of pull requests that are significantly behind main branch or stale.

## Detection Criteria

A PR is marked **outdated** if either:
- **>COMMIT_THRESHOLD commits** behind the base branch (configurable via `COMMIT_THRESHOLD`, defaults to `50`)
- **>STALE_DAYS_THRESHOLD days** since last commit (configurable via `STALE_DAYS_THRESHOLD`, defaults to `7`)

Base branch is configurable via `BASE_BRANCH` (defaults to `main`).

## Structure

```
outdated-pr-scripts/
├── process-pr.sh              # Main processing script
└── lib/
    ├── check-pr-commits.sh    # Calculate commits behind
    └── update-outdated-pr.sh  # Add labels/comments
```

## Usage

### Workflow Integration

```yaml
- name: Process PRs
  env:
    GH_TOKEN: ${{ github.token }}
    DRY_RUN: ${{ inputs.dry_run == 'live' && 'false' || 'true' }}
  run: |
    echo '${{ needs.get-prs.outputs.pr_list }}' | \
      ./.github/outdated-pr-scripts/process-pr.sh
```

### Manual Testing

```bash
# Test with sample data (using default main branch, 50 commit threshold, 7 days staleness)
echo '[{"pr_number": 123, "branch_name": "feature", "repo_owner": "camunda"}]' | \
  ./.github/outdated-pr-scripts/process-pr.sh

# Test with custom thresholds
BASE_BRANCH=develop COMMIT_THRESHOLD=25 STALE_DAYS_THRESHOLD=14 echo '[{"pr_number": 123, "branch_name": "feature", "repo_owner": "camunda"}]' | \
  ./.github/outdated-pr-scripts/process-pr.sh
```

**Script Output:**
- **Processing logs**: Step-by-step status (e.g., "🔍 Processing PR #123", "📊 Calculating commits behind")
- **Commit calculations**: Exact counts (e.g., "📈 PR #123 is 25 commits behind main" or custom base branch)
- **Actions taken**: Label/comment status (e.g., "🏷️ Adding outdated-branch label" or "🔍 DRY-RUN: Would add label")
- **Summary statistics**: Total counts and detailed tables (in daily GitHub Actions runs)

## Environment Variables

- `GH_TOKEN`: GitHub token for API access (required)
- `GITHUB_REPOSITORY`: Repository in `owner/repo` format (required)
- `DRY_RUN`: Set to `true` for testing without changes (optional)
- `BASE_BRANCH`: Target branch for comparison (optional, defaults to `main`)
- `COMMIT_THRESHOLD`: Number of commits behind to trigger outdated status (optional, defaults to `50`)
- `STALE_DAYS_THRESHOLD`: Number of days since last commit to trigger outdated status (optional, defaults to `7`)

## Features

- **Smart filtering**: Excludes renovate bot commits
- **Automatic labeling**: Adds `outdated-branch` label
- **Daily summaries**: GitHub Actions step summary with statistics
- **Dry-run support**: Safe testing mode
- **Duplicate prevention**: Won't spam with multiple comments

## Dependencies

- `git`, `gh` (GitHub CLI), `jq`, `date`/`gdate`
- Repository with full git history (`fetch-depth: 0`)
- GitHub token with PR write permissions

