```yaml
---
applyTo: "zeebe/backup-stores/common/**"
---
```
# Zeebe Backup Stores Common Module

## Purpose

This module provides shared domain types and state machine logic used by all backup store implementations (Azure, GCS, S3, filesystem). It bridges the `zeebe-backup` API interfaces with concrete serializable records, and defines the `Manifest` — a sealed-interface state machine that governs backup lifecycle transitions. Every backup store depends on this module for manifest management, file set tracking, and structured error handling.

## Architecture

The module has three concerns:
1. **Manifest state machine** (`Manifest` sealed interface + `ManifestImpl` record) — enforces valid backup lifecycle transitions
2. **Serializable file tracking** (`FileSet` record) — converts API `NamedFileSet` into a persistable list of file names with uniqueness validation
3. **Structured exceptions** (`BackupStoreException`) — typed error hierarchy for invalid manifest states

Package: `io.camunda.zeebe.backup.common` (shared with `zeebe/backup/src/main/java/.../common/` which contains the API impl records like `BackupIdentifierImpl`, `BackupImpl`, etc.).

## Key Abstractions

### Manifest State Machine (`Manifest.java`)
- Sealed interface with four sub-interfaces: `InProgressManifest`, `CompletedManifest`, `FailedManifest`, `DeletedManifest`.
- All four are permitted only by `ManifestImpl` — a single record that implements all four.
- State is tracked via `StatusCode` enum, not the Java type — `as*()` methods perform runtime state checks and throw `UnexpectedManifestState` on mismatch.
- Transitions are immutable: `complete()`, `fail(reason)`, `delete()` return new `ManifestImpl` instances with updated `statusCode` and `modifiedAt`.
- Valid transitions: `IN_PROGRESS → COMPLETED|FAILED|DELETED`, `COMPLETED → FAILED|DELETED`, `FAILED → DELETED`. `DELETED` is terminal.
- Jackson serialization is configured via `@JsonSerialize(as = ManifestImpl.class)` / `@JsonDeserialize(as = ManifestImpl.class)` on the sealed interface.
- Factory methods: `Manifest.createInProgress(Backup)` and `Manifest.createFailed(BackupIdentifier)`.
- `Manifest.toStatus(Manifest)` converts any manifest state to a `BackupStatusImpl` for API responses.

### ManifestImpl Invariant (`ManifestImpl.java`)
- Compact constructor enforces: `failureReason` must be `null` unless `statusCode == FAILED`. Violation throws `InvalidPersistedManifestState`.
- `createdAt` is set once at creation; `modifiedAt` updates on every transition via `Instant.now()`.

### FileSet (`FileSet.java`)
- Record wrapping `List<NamedFile>` with a compact constructor that validates file name uniqueness.
- `FileSet.of(NamedFileSet)` factory converts the API's `Map<String, Path>` into a serializable list of `NamedFile(String name)` records.
- Null `NamedFileSet` input produces an empty `FileSet`.

### BackupStoreException (`BackupStoreException.java`)
- Abstract base `RuntimeException` with two concrete subclasses:
  - `InvalidPersistedManifestState` — corrupt/invalid deserialized manifest (e.g., `failureReason` set on non-FAILED manifest)
  - `UnexpectedManifestState` — wrong state for requested operation (e.g., calling `asCompleted()` on an IN_PROGRESS manifest)

## Relationship to Other Modules

- **Depends on**: `zeebe-backup` (API interfaces: `Backup`, `BackupIdentifier`, `BackupDescriptor`, `NamedFileSet`, `BackupStatus`, `BackupStatusCode`), Jackson (`jackson-databind` for manifest serialization)
- **Consumed by**: All backup store implementations — `azure/`, `gcs/`, `s3/`, `filesystem/` — import `Manifest`, `ManifestImpl`, `FileSet`, and `BackupStoreException` for their `ManifestManager` and `FileSetManager` classes
- **Sibling common types** (in `zeebe/backup`): `BackupIdentifierImpl`, `BackupDescriptorImpl`, `BackupImpl`, `BackupStatusImpl`, `NamedFileSetImpl` — these are API implementation records, not part of this module but share the `io.camunda.zeebe.backup.common` package

## Extension Points

- To add a new backup state: add a variant to `StatusCode` enum, create a new sealed sub-interface in `Manifest`, implement it in `ManifestImpl`, and add the corresponding case in `Manifest.toStatus()`.
- To add new manifest metadata: add fields to `ManifestImpl` record and update the convenience constructor and factory methods.

## Invariants

- Every state transition produces a new immutable `ManifestImpl` — never mutate an existing manifest.
- `failureReason` is non-null only when `statusCode == FAILED`; the compact constructor enforces this.
- `FileSet` file names must be unique; duplicate names throw `IllegalArgumentException` at construction time.
- `createdAt` is immutable after initial creation; only `modifiedAt` changes on transitions.
- Always use `as*()` methods to safely downcast a `Manifest` to a specific state — they throw `UnexpectedManifestState` rather than silently returning an incorrect type.

## Common Pitfalls

- Do not add mutable fields to `ManifestImpl` — all transitions must create new instances.
- Do not set `failureReason` on non-FAILED manifests — the compact constructor will throw.
- Do not assume `ManifestImpl` can be cast by Java type alone — all four sub-interfaces are the same runtime class; use `statusCode()` or `as*()` for state discrimination.
- When deserializing manifests from storage, rely on Jackson's `@JsonDeserialize(as = ManifestImpl.class)` — the compact constructor validates invariants on deserialization.

## Testing Patterns

- Tests use JUnit 5 with AssertJ assertions and `// given`, `// when`, `// then` sections.
- `ManifestStateTransitionTest` validates all legal state transitions and timestamp invariants.
- `ManifestStateCastingTest` validates that `as*()` methods throw `UnexpectedManifestState` for every illegal state cast.
- `FileSetTest` validates uniqueness enforcement and `NamedFileSet` conversion.
- Test data uses `BackupIdentifierImpl`, `BackupDescriptorImpl`, `BackupImpl` from the sibling `zeebe-backup` module.
- Run scoped: `./mvnw -pl zeebe/backup-stores/common -am test -DskipITs -DskipChecks -T1C`

## Key Files

| File | Role |
|------|------|
| `src/main/java/.../Manifest.java` | Sealed interface defining the backup lifecycle state machine and `toStatus()` conversion |
| `src/main/java/.../ManifestImpl.java` | Single record implementing all four manifest states with immutable transitions |
| `src/main/java/.../FileSet.java` | Serializable file name list with uniqueness validation |
| `src/main/java/.../BackupStoreException.java` | Typed exception hierarchy for manifest errors |
| `src/test/java/.../ManifestStateTransitionTest.java` | Comprehensive state transition and timestamp tests |