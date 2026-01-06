# Version Compatibility Matrix Caching

The `VersionCompatibilityMatrix` uses a two-level caching strategy to minimize GitHub API calls:

1. **Application-level cache**: The `CachedVersionProvider` decorator caches discovered versions (with metadata) to a local JSON file
2. **CI-level cache**: GitHub Actions can cache this file across workflow runs for even faster execution

## How It Works

**First discovery** (cold start):
- Fetches version tags from GitHub API
- For each latest patch: checks if a release exists (additional API call)
- Saves complete metadata (version, isReleased, isLatest) to `.cache/camunda/camunda-versions.json`

**Subsequent runs** (warm cache):
- Reads from cached file
- Zero GitHub API calls
- Instant version discovery

## Cache Location

The cache file is stored at: `.cache/camunda/camunda-versions.json` (relative to a given location, see below)

**Path Resolution Strategy** (in priority order):
1. **`GITHUB_WORKSPACE`**: Environment variable set in GitHub Actions workflows
2. **`maven.multiModuleProjectDirectory`**: Maven property pointing to the multi-module project root
- This is passed to the test JVM via `maven-failsafe-plugin` configuration in `pom.xml`
3. **Fallback to `user.dir`**: If none of the above work (should never happen in normal scenarios)

**Note for GitHub Actions**:
- Make sure the `GH_TOKEN` is set in order to not run into GitHub API rate limits
- Configure cache actions to use path `.cache/camunda` relative to the repository root and `GITHUB_WORKSPACE` is set
- The cache action's "save" step runs **after** tests complete, so the directory will exist if tests ran successfully
- On first run (no cache), the directory is created during test execution

## Local Development

### View Cached Versions

```bash
cat .cache/camunda/camunda-versions.json | jq .
```

### Force Cache Refresh

```bash
rm -f .cache/camunda/camunda-versions.json
./mvnw test -pl zeebe/qa/update-tests
```

### Disable Caching (for debugging)

Option 1: Delete the cache file before running tests

```bash
rm -rf .cache/camunda
```

Option 2: Use the provider directly in your test:

```java
// Bypass caching by passing the provider directly
new VersionCompatibilityMatrix(new GithubVersionProvider())
```

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
    path: .cache/camunda
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
          path: .cache/camunda/camunda-versions.json

  test:
    needs: prepare-versions
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Download version cache
        uses: actions/download-artifact@v4
        with:
          name: version-cache
          path: .cache/camunda

      - name: Run tests
        run: ./mvnw verify -pl zeebe/qa/update-tests
```

