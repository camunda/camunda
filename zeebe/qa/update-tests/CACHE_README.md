# Version Compatibility Matrix Caching

## Overview

The `VersionCompatibilityMatrix` uses a two-level caching strategy to minimize GitHub API calls:

1. **Application-level cache**: The `CachedVersionProvider` decorator caches discovered versions (with metadata) to a local JSON file
2. **CI-level cache**: GitHub Actions can cache this file across workflow runs for even faster execution

### How It Works

**First discovery** (cold start):
- Fetches version tags from GitHub API
- For each latest patch: checks if a release exists (additional API call)
- Saves complete metadata (version, isReleased, isLatest) to `.cache/camunda-versions.json`

**Subsequent runs** (warm cache):
- Reads from cached file
- Zero GitHub API calls
- Instant version discovery

**With GitHub Actions caching**:
- First workflow run: Creates cache file
- Later workflow runs: Restores cache file from GitHub Actions cache
- Cache persists across workflow runs (until manually invalidated or expired)
- **Recommended**: Use hourly cache refresh to balance freshness and performance

This approach pairs perfectly with GitHub Actions caching - the application creates the cache file, and GitHub Actions persists it across runs.

### Architecture

```
VersionCompatibilityMatrix
  └── CachedVersionProvider (decorator)
       └── GithubVersionProvider (delegate)
```

The `CachedVersionProvider`:
- Intercepts version discovery calls
- Returns cached data when available
- Delegates to `GithubVersionProvider` only on cache miss
- Automatically saves fetched data for future use

## Cache Location

The cache file is stored at: `.cache/camunda-versions.json`

This file contains a JSON array of version strings discovered from GitHub tags.

## Cache Behavior

### GitHub API Authentication

The version discovery automatically uses the `GH_TOKEN` environment variable if available for authenticated GitHub API requests. This provides:
- **Higher rate limits**: 5,000 requests/hour (vs 60 for unauthenticated)
- **More reliable**: Less likely to hit rate limiting issues
- **Better for CI**: Reduces the chance of hitting rate limits in workflows

**Local usage:**
```bash
export GH_TOKEN=your_github_token
```

**In GitHub Actions:**
```yaml
env:
  GH_TOKEN: ${{ github.token }}
```

### Application Cache

**Cache Miss** (file doesn't exist):
1. Fetches version tags from GitHub
2. Filters pre-releases and null versions
3. Identifies latest patch for each minor version
4. Checks release status (only for latest patches)
5. Saves metadata to `.cache/camunda-versions.json`

**Cache Hit** (file exists):
1. Loads data from `.cache/camunda-versions.json`
2. Zero GitHub API calls
3. Returns cached version metadata

### With GitHub Actions

GitHub Actions can persist the `.cache/` directory across workflow runs:
- **First run**: Creates cache, saves to GitHub Actions cache
- **Later runs**: Restores from GitHub Actions cache, instant results
- **Manual refresh**: Delete cache artifact or `.cache/` directory

This two-level caching (application + CI) ensures minimal API usage and fast test execution.

## GitHub Actions Integration

### Option 1: Hourly Cache Refresh (Recommended)

To automatically refresh the cache every hour while still benefiting from caching:

```yaml
- name: Generate cache key with current hour
  id: cache-key
  run: echo "hour=$(date -u +%Y%m%d%H)" >> $GITHUB_OUTPUT

- name: Cache Zeebe versions (hourly refresh)
  uses: actions/cache@v4
  with:
    path: .cache
    key: zeebe-versions-${{ steps.cache-key.outputs.hour }}
    restore-keys: |
      zeebe-versions-
```

This ensures:
- Cache is invalidated every hour (new cache key each hour)
- Multiple workflow runs within the same hour share the cache
- Automatic refresh keeps version data current
- No manual intervention needed

### Option 2: Manual Cache Control

Generate the cache file once in a dedicated job:

```yaml
jobs:
  prepare-versions:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Generate version cache
        run: |
          ./mvnw test -pl zeebe/qa/update-tests -Dtest=VersionCompatibilityMatrixTest#testDiscoverVersions -DfailIfNoTests=false

      - name: Upload cache
        uses: actions/upload-artifact@v4
        with:
          name: version-cache
          path: .cache/zeebe-versions.json

  test:
    needs: prepare-versions
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Download version cache
        uses: actions/download-artifact@v4
        with:
          name: version-cache
          path: .cache

      - name: Run tests
        run: ./mvnw verify -pl zeebe/qa/update-tests
```

## Local Development

### View Cached Versions

```bash
cat .cache/zeebe-versions.json | jq .
```

### Force Cache Refresh

```bash
rm -f .cache/zeebe-versions.json
./mvnw test -pl zeebe/qa/update-tests
```

### Disable Caching (for debugging)

Option 1: Delete the cache file before running tests
```bash
rm -rf .cache
```

Option 2: Use the provider directly in your test:
```java
// Bypass caching by passing the provider directly
new VersionCompatibilityMatrix(new GithubVersionProvider())
```

## Cache File Format

The cache file (`.cache/camunda-versions.json`) is a JSON array of version metadata:

```json
[
  {
    "version": "8.0.0",
    "isReleased": true,
    "isLatest": false
  },
  {
    "version": "8.0.1",
    "isReleased": true,
    "isLatest": true
  },
  {
    "version": "8.1.0",
    "isReleased": false,
    "isLatest": true
  }
]
```

