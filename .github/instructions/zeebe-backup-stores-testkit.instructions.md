```yaml
---
applyTo: "zeebe/backup-stores/testkit/**"
---
```
# Backup Store Testkit — Module Instructions

## Purpose

This module is a **contract test kit** for `BackupStore` implementations. It provides reusable JUnit 5 test interfaces with default methods that verify any `BackupStore` implementation conforms to the behavioral contract expected by Zeebe. It is consumed as a `test-jar` dependency by concrete store modules (filesystem, S3, Azure, GCS).

## Architecture

The testkit uses **interface-based test inheritance** — each behavioral concern is a separate interface with `default` test methods. A single composite interface (`BackupStoreTestKit`) aggregates all concerns. Implementors create a test class that `implements BackupStoreTestKit`, supply a `BackupStore` via `getStore()`, and inherit ~20 parameterized/unit tests automatically.

### Component Hierarchy

```
BackupStoreTestKit (composite interface)
├── SavingBackup          — save, duplicate rejection, missing files, no-overwrite
├── DeletingBackup        — delete nonexistent, delete after save
├── RestoringBackup       — restore content fidelity, path isolation
├── UpdatingBackupStatus  — completed after save, markFailed, timestamp update
├── QueryingBackupStatus  — getStatus, failed status, nonexistent status
├── ListingBackups        — wildcard filtering (node/partition/checkpoint patterns)
└── StoringBackupMetadata — store/load/overwrite/partition-isolation of metadata bytes
```

### Support Classes (`support/` package)

- **`TestBackupProvider`**: Factory for test `Backup` fixtures with randomized file content. Provides `simpleBackup()` (snapshot+segments), `backupWithoutSnapshot()`, and `minimalBackupWithId()`. Uses a shared `TEMP_DIR` with shutdown hook cleanup.
- **`WildcardBackupProvider`**: JUnit `ArgumentsProvider` generating 8 wildcard search scenarios covering all combinations of optional nodeId/partitionId/checkpointPattern.
- **`BackupAssert`**: Custom AssertJ assertion for `Backup` — verifies id, descriptor, and delegates file content comparison to `NamedFileSetAssert`.
- **`NamedFileSetAssert`**: Custom AssertJ assertion verifying binary file content equality and path residency.

## Key Abstractions

- **`BackupStore`** (`zeebe/backup/src/main/java/.../api/BackupStore.java`): The SPI interface under test. All methods return `CompletableFuture`. Key operations: `save`, `getStatus`, `list`, `delete`, `restore`, `markFailed`, `markDeleted`, `storeBackupMetadata`, `loadBackupMetadata`.
- **`BackupIdentifier`**: Record-like triple of `(nodeId, partitionId, checkpointId)`.
- **`BackupIdentifierWildcard`**: Wildcard query with `Optional` fields and `CheckpointPattern` (exact, prefix, any).
- **`Backup`**: Composite of identifier, descriptor, snapshot `NamedFileSet`, and segments `NamedFileSet`.
- **`BackupStatusCode`**: Enum — `DOES_NOT_EXIST`, `DELETED`, `FAILED`, `IN_PROGRESS`, `COMPLETED`.

## Consumers

Concrete `BackupStore` integration tests implement `BackupStoreTestKit`:
- `zeebe/backup-stores/filesystem/.../FilesystemBackupStoreIT.java`
- `zeebe/backup-stores/azure/.../AzureBackupStoreIT.java`
- `zeebe/backup-stores/gcs/.../GcsBackupStoreIT.java`

The S3 backup store module also exists at `zeebe/backup-stores/s3/`.

## Design Patterns

- **Interface default methods as contract tests**: Each interface defines `default` test methods that run against the `BackupStore` returned by the abstract `getStore()` method. This is a variant of the Template Method pattern using interfaces.
- **Parameterized tests via `@MethodSource`**: Most tests use `@MethodSource("provideBackups")` which resolves to `BackupStoreTestKit.provideBackups()` — a static method delegating to `TestBackupProvider`.
- **`@ArgumentsSource` for complex scenarios**: `ListingBackups` uses `WildcardBackupProvider` as a custom `ArgumentsProvider` for wildcard search test cases.
- **Custom AssertJ assertions**: `BackupAssert` and `NamedFileSetAssert` provide fluent, domain-specific assertion chains.
- **Test-jar packaging**: The POM uses `maven-jar-plugin` `test-jar` goal so test classes are published as a dependency artifact. JUnit dependencies are `compile`-scoped (not `test`) to ensure transitivity.

## Extension Points

### Adding a New Behavioral Contract
1. Create a new interface in `io.camunda.zeebe.backup.testkit` (e.g., `CompressingBackup`).
2. Define `BackupStore getStore()` and add `default` test methods with `@Test` or `@ParameterizedTest`.
3. Add the new interface to `BackupStoreTestKit`'s `extends` clause.
4. All existing implementors automatically inherit the new tests.

### Adding New Test Fixtures
Add factory methods to `TestBackupProvider`. Use `Files.createTempDirectory(TEMP_DIR, ...)` for file isolation and `RandomUtils.nextBytes()` for content. Register new fixtures in `provideArguments()`.

### Implementing for a New Store
1. Create an integration test class implementing `BackupStoreTestKit`.
2. Override `getStore()` to return a fresh store instance (clean state per test via `@BeforeEach`).
3. Override `getBackupInInvalidStateExceptionClass()` and `getFileNotFoundExceptionClass()` to return store-specific exception types.
4. Add the `test-jar` dependency: `<classifier>tests</classifier>` on `zeebe-backup-testkit`.

## Invariants

- Every test expects a **clean store per test** — implementors must reset state in `@BeforeEach`.
- `save()` must be idempotent-safe: saving a completed backup again must fail (not silently overwrite).
- `delete()` on a nonexistent backup must succeed (no-op semantics).
- `getStatus()` for nonexistent backups must return `BackupStatusCode.DOES_NOT_EXIST` (never throw).
- `restore()` must produce files with identical binary content to the original, located under the target directory.
- `markFailed()` must update `lastModified` timestamp to be strictly after the initial save timestamp.
- Metadata operations (`storeBackupMetadata`/`loadBackupMetadata`) must be isolated per partition ID.
- All `BackupStore` methods return `CompletableFuture` — tests use `assertThat(...).succeedsWithin(Duration)` or `.join()`, never `Thread.sleep`.

## Common Pitfalls

- **Forgetting exception class overrides**: `SavingBackup` requires `getBackupInInvalidStateExceptionClass()` and `getFileNotFoundExceptionClass()` — these are store-specific and must be provided by each implementor.
- **Stale temp files**: `TestBackupProvider` uses a shared static `TEMP_DIR` with a JVM shutdown hook. Do not assume temp files persist across JVM restarts.
- **Timestamp precision**: `BackupDescriptorImpl` truncates `Instant` to `ChronoUnit.MILLIS` — store implementations must preserve at least millisecond precision.
- **Compile-scoped JUnit**: JUnit dependencies are intentionally `compile`-scoped for test-jar transitivity. Do not change them to `test` scope.

## Key Files

| File | Role |
|------|------|
| `src/test/java/.../BackupStoreTestKit.java` | Composite interface aggregating all 7 test contracts |
| `src/test/java/.../SavingBackup.java` | Save contract: success, duplicate rejection, missing files, no-overwrite |
| `src/test/java/.../support/TestBackupProvider.java` | Test fixture factory (simple, no-snapshot, minimal backups) |
| `src/test/java/.../support/BackupAssert.java` | Custom AssertJ assertion for `Backup` content and path verification |
| `pom.xml` | Test-jar packaging config; compile-scoped JUnit for transitivity |