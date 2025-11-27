# Add Release Comments to Issues Workflow

This GitHub Actions workflow automatically adds comments to issues that have been resolved in recent releases.

## Overview

The workflow runs monthly on the second Wednesday of each month and:

1. Looks for all releases published since the last run (default: 35 days back)
2. Extracts issue references from the release notes/changelog
3. For each referenced issue (excluding pull requests):
   - Verifies it's a closed issue
   - Checks if a release comment already exists
   - Adds a standardized comment linking to the release

## Comment Format

The workflow adds comments in this format:

```
This has been released in version [8.8.4](https://github.com/camunda/camunda/releases/tag/8.8.4). See release notes [here](https://github.com/camunda/camunda/releases/tag/8.8.4) for details.
```

## Schedule

- **Automatic**: Second Wednesday of each month at 9:00 AM UTC
- **Manual**: Can be triggered via workflow_dispatch with custom parameters

## Manual Execution

The workflow can be manually triggered with these optional parameters:

- `days_back`: Number of days to look back for releases (default: 35)
- `dry_run`: If true, logs what would be done without posting comments (default: false)

## Issue Detection

The workflow extracts issue numbers from release notes using multiple patterns:

- `#1234` - Standard GitHub issue references
- `camunda/camunda#1234` - Full repository references
- `issues/1234` or `issue/1234` - Alternative formats
- `[#1234](url)` - Markdown linked references
- Full GitHub URLs to issues

## Safety Features

- **Only processes closed issues** - Open issues are skipped
- **Excludes pull requests** - Only actual issues receive comments
- **Duplicate prevention** - Checks for existing release comments before adding new ones
- **Dry run support** - Test the workflow without making changes
- **Rate limiting aware** - Uses GitHub CLI which handles API rate limits

## Testing

Use the included test script to validate the workflow logic:

```bash
# Test with releases from the last 7 days
./test-release-comments.sh 7

# Test with releases from the last 30 days
./test-release-comments.sh 30
```

The test script requires:
- GitHub CLI (`gh`) installed and authenticated
- Access to the repository
- `jq` for JSON parsing

## Permissions

The workflow requires these permissions:
- `issues: write` - To add comments to issues
- `contents: read` - To read repository content and releases

## Dependencies

- GitHub CLI (`gh`) for API interactions
- `jq` for JSON parsing
- Standard Unix tools (`grep`, `sort`, `date`)

## Error Handling

The workflow includes error handling for:
- Missing or inaccessible issues
- API rate limiting
- Network timeouts
- Malformed release data

## Monitoring

The workflow includes build status observation for analytics and monitoring purposes, following the repository's standard practices.