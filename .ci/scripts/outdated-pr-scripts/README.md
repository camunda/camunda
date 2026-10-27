# Outdated PR Detection Scripts

Automated detection and labeling of pull requests that are significantly behind main branch or stale.

## Detection Criteria

A PR is marked **outdated** if either:
- **>COMMIT_THRESHOLD commits** behind its base branch (configurable via `COMMIT_THRESHOLD`, defaults to `50`)
- **>STALE_DAYS_THRESHOLD days** since last commit (configurable via `STALE_DAYS_THRESHOLD`, defaults to `7`)

When **both conditions** are met, the comment will indicate both issues: "X commits behind and Y days stale".

The base branch is automatically derived from each PR's target branch.

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
    DRY_RUN: ${{ inputs.dry_run == false && 'false' || 'true' }}
  run: |
    echo '${{ needs.get-prs.outputs.pr_list }}' | \
      ./.ci/scripts/outdated-pr-scripts/process-pr.sh
```

### Manual Testing

```bash
# Test with sample data (using default 50 commit threshold, 7 days staleness)
echo '[{"pr_number": 123, "branch_name": "feature", "base_branch": "main", "repo_owner": "camunda"}]' | \
  ./.ci/scripts/outdated-pr-scripts/process-pr.sh

# Test with custom thresholds
COMMIT_THRESHOLD=25 STALE_DAYS_THRESHOLD=14 echo '[{"pr_number": 123, "branch_name": "feature", "base_branch": "develop", "repo_owner": "camunda"}]' | \
  ./.ci/scripts/outdated-pr-scripts/process-pr.sh
```

**Script Output:**
- **Processing logs**: Step-by-step status (e.g., "🔍 Processing PR #123", "📊 Calculating commits behind")
- **Commit calculations**: Exact counts (e.g., "� Found 25 non-renovate commits behind main")
- **Actions taken**: Label/comment status (e.g., "🏷️ Adding outdated-branch label" or "🔍 DRY-RUN: Would add label")

## Environment Variables

- `GH_TOKEN`: GitHub token for API access (required)
- `GITHUB_REPOSITORY`: Repository in `owner/repo` format (auto-detected in GitHub Actions, optional for local testing)
- `DRY_RUN`: Set to `true` for testing without changes (optional)
- `COMMIT_THRESHOLD`: Number of commits behind to trigger outdated status (optional, defaults to `50`)
- `STALE_DAYS_THRESHOLD`: Number of days since last commit to trigger outdated status (optional, defaults to `7`)

## Features

- **Smart filtering**: Excludes renovate bot commits
- **Automatic labeling**: Adds `outdated-branch` label
- **Dynamic commenting**: Creates or updates comments with current status
- **Dual-threshold logic**: Combines commit count and staleness detection
- **Dry-run support**: Safe testing mode with boolean checkbox input
- **Duplicate prevention**: Updates existing comments instead of creating new ones

## Dependencies

- `git`, `gh` (GitHub CLI), `jq`, `date`/`gdate`
- Repository with full git history (`fetch-depth: 0`)
- GitHub token with PR write permissions

