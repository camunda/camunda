```yaml
---
applyTo: "zeebe/qa/update-tests/**"
---
```
# Zeebe QA Update Tests

## Purpose

This module verifies that Zeebe brokers can be upgraded across versions without data loss or behavioral regression. It covers single-node upgrades (with/without snapshots), rolling updates in a 3-node Raft cluster, old-gateway-to-new-broker compatibility, and cross-version backup restore/delete for S3, GCS, and Azure storage backends. All tests use Testcontainers to run real Docker images of previous Zeebe versions.

## Architecture

The module has two subsystems: **upgrade tests** (root package) and **backup compatibility tests** (`backup/` subpackage).

### Upgrade Tests — Component Overview

- **`ContainerState`** — Manages lifecycle of a single `ZeebeContainer` broker + optional `ZeebeGatewayContainer`. Provides fluent builder methods (`withOldBroker()`, `withNewBroker()`, `withOldGateway()`) and log-scraping methods (`hasElementInState`, `hasMessageInState`, `getIncidentKey`) for assertion. Uses `ZeebeVolume` to persist data across container restarts. Configures containers with auth disabled, no secondary storage, and no REST.
- **`ContainerStateExtension`** — JUnit 5 extension that injects fresh `ContainerState` instances via parameter resolution and dumps container logs on test failure via `AfterTestExecutionCallback`.
- **`ContainerStateAssert`** — Custom AssertJ assertion class for `ContainerState`. Provides `hasSnapshotAvailable`, `hasCompletedProcess`, and `eventually*` variants using Awaitility internally.
- **`UpdateTestCase` / `UpdateTestCaseProvider`** — Builder-based test scenario framework. Each scenario defines `deployProcess`, `createInstance`, `beforeUpgrade` (leaves process in a waiting state), and `afterUpgrade` (completes the process). `UpdateTestCaseProvider` implements `ArgumentsProvider` to feed ~14 parameterized scenarios covering jobs, messages, timers, incidents, call activities, gateways, compensation, ad-hoc sub-processes, and resource distribution.
- **`VersionCompatibilityMatrix`** — Discovers compatible version pairs by querying the GitHub API for tags. Supports multiple matrix strategies: `fromPreviousMinorToCurrent` (local dev), `fromFirstAndLastPatchToCurrent` (CI), `fromPreviousPatchesToCurrent` (periodic), `full` (all valid upgrade paths with sharding). Maintains an `INCOMPATIBLE_UPGRADES` list for known-broken upgrade paths. Uses a decorator chain: `GithubVersionProvider` → `ReleaseVerifiedGithubVersionProvider` → `CachedVersionProvider`.

### Test Classes

| Class | Type | Cluster Shape | What It Tests |
|---|---|---|---|
| `SnapshotTest` | Parameterized IT | Single-node, old→new | Upgrade with snapshot present; also tests snapshot-after-upgrade |
| `NoSnapshotTest` | Parameterized IT | Single-node, old→new | Upgrade from log replay only (no snapshot) |
| `OldGatewayTest` | Parameterized IT | Single-node, new broker + old gateway | Old gateway compatibility with new broker |
| `RollingUpdateTest` | Parameterized IT | 3-node cluster | Version restart, snapshot replication, full rolling update with job completion |

### Backup Compatibility Tests

- **`BackupCompatibilityAcceptance`** — Java interface with default `@Test` methods. Defines the test flow: start old broker in Docker → deploy process → take backup → restore with current version `TestRestoreApp` → start current broker on restored data → verify job completion. Also tests backup deletion across versions.
- **`S3BackupCompatibilityIT`** — Implements the interface using `MinioContainer` for S3-compatible storage.
- **`GcsBackupCompatibilityIT`** — Uses `GcsContainer` (fake-gcs-server) for Google Cloud Storage.
- **`AzureBackupCompatibilityIT`** — Uses `AzuriteContainer` for Azure Blob Storage.

## Key Patterns

### Version Resolution
Previous version comes from `VersionUtil.getPreviousVersion()`. Current version uses `ZeebeTestContainerDefaults.defaultTestImage()` (tag `current-test`). The `CURRENT` sentinel in `RollingUpdateTest` maps to the local Docker image with a version override stripping `-SNAPSHOT`.

### Container Restart for Upgrade
Upgrade tests close `ContainerState`, reconfigure with `withNewBroker()`, and restart. The `ZeebeVolume` persists data across restarts, simulating an in-place upgrade.

### Log Scraping for Assertions
`ContainerState.hasElementInState()` and `hasLogContaining()` parse raw container logs line-by-line for JSON fragments. This avoids needing an exporter — tests check intent strings like `"ELEMENT_COMPLETED"`, `"INCIDENT"`, `"TIMER"`.

### Version Compatibility Caching
`CachedVersionProvider` stores discovered versions to `.cache/camunda/camunda-versions.json`. Path resolves from `GITHUB_WORKSPACE` → `maven.multiModuleProjectDirectory` → `user.dir`. Set `GH_TOKEN` env var to avoid GitHub API rate limits. See `CACHE_README.md`.

### Environment-Driven Matrix Selection
`VersionCompatibilityMatrix.auto()` selects strategy based on env vars: `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY` → `full()`, `ZEEBE_CI_CHECK_CURRENT_VERSION_COMPATIBILITY` → `fromPreviousPatchesToCurrent()`, `CI` → `fromFirstAndLastPatchToCurrent()`, else → `fromPreviousMinorToCurrent()`.

## Extension Points

### Adding a New Upgrade Scenario
Add a new `scenario()` call in `UpdateTestCaseProvider.provideArguments()`. Use the builder: `.name(...)`, `.deployProcess(...)`, `.createInstance(...)`, `.beforeUpgrade(...)` (return a key for use after upgrade), `.afterUpgrade(...)`, `.done()`. The process must be left in a waiting state before upgrade and completed after.

### Adding Known Incompatible Upgrades
Add an `UpgradePath` entry to `INCOMPATIBLE_UPGRADES` in `VersionCompatibilityMatrix`. The `from` field is the first affected source patch; the `to` field is the first *compatible* target patch.

### Adding a New Backup Store Test
Create a new `*IT` class implementing `BackupCompatibilityAcceptance`. Provide: `getNetwork()`, `oldBrokerBackupStoreEnvVars()` (legacy `ZEEBE_BROKER_DATA_BACKUP_*` env vars), and `configureCurrentBackupStore(Camunda cfg)` (unified config API). Annotate with `@Testcontainers` and `@ZeebeIntegration`.

## Common Pitfalls

- **Missing Docker image**: Tests require `camunda/zeebe:current-test` to exist. Build it first: `docker build --build-arg DISTBALL='dist/target/camunda-zeebe*.tar.gz' -t camunda/zeebe:current-test --target app .`
- **SnapshotTest is `@Disabled`**: It requires previous version artifacts to be published. Do not remove the annotation without confirming artifact availability.
- **Timeouts with remote debugging**: Break points only pause the remote JVM — Awaitility timeouts still apply locally. Increase all timeouts when debugging.
- **User/group workarounds**: `ContainerState` and `RollingUpdateTest` set `withUser("1001:0")` for compatibility across the 8.3→8.4 user ID change. These are marked with TODO comments.
- **Migration tests excluded**: `maven-failsafe-plugin` in `pom.xml` explicitly excludes `io.camunda.zeebe.test.migration.**` from execution.

## Build and Run

```bash
# Build the required Docker image first
./mvnw install -DskipChecks -DskipTests -T1C
docker build -f camunda.Dockerfile --target app -t camunda/zeebe:current-test --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .

# Run upgrade tests (unit test phase, not ITs)
./mvnw -pl zeebe/qa/update-tests -am test -DskipITs -DskipChecks -T1C

# Run backup compatibility ITs
./mvnw -pl zeebe/qa/update-tests -am verify -DskipUTs -DskipChecks -T1C

# Run a specific test class
./mvnw -pl zeebe/qa/update-tests -am test -DskipITs -DskipChecks -Dtest=RollingUpdateTest -T1C
```

## Key Files

- `ContainerState.java` — Docker container lifecycle and log-scraping assertions
- `UpdateTestCaseProvider.java` — All upgrade test scenarios (~14 BPMN patterns)
- `VersionCompatibilityMatrix.java` — Version discovery, caching, compatibility filtering, sharding
- `RollingUpdateTest.java` — 3-node cluster rolling update tests
- `BackupCompatibilityAcceptance.java` — Interface defining cross-version backup/restore test flow