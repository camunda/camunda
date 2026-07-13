```yaml
---
applyTo: "zeebe/exporter-common/src/**"
---
```
# Zeebe Exporter Common — Module Instructions

## Purpose

Shared infrastructure library consumed by the Camunda Exporter (`zeebe/exporters/camunda-exporter`) and RDBMS Exporter (`zeebe/exporters/rdbms-exporter`). Provides three concerns: entity caching with lazy backend loading, audit log transformation from Zeebe records, and history deletion configuration. Also consumed by `configuration/` for property binding.

## Architecture

```
io.camunda.zeebe.exporter.common/
├── cache/                        # Generic entity cache (Caffeine-backed)
│   ├── ExporterEntityCache<K,T>          # Interface: get, getAll, put, remove, clear
│   ├── ExporterEntityCacheImpl<K,T>      # Caffeine LoadingCache with stats
│   ├── process/CachedProcessEntity       # Record: name, version, versionTag, callElementIds, flowNodesMap
│   ├── process/ProcessDiagramData        # Record: callActivityIds, flowNodesMap (extracted from BPMN XML)
│   ├── batchoperation/CachedBatchOperationEntity  # Record: batchOperationKey, type
│   └── decisionRequirements/CachedDecisionRequirementsEntity  # Record: key, name, version
├── auditlog/                     # Audit log entry creation and filtering
│   ├── AuditLogCheck             # Functional interface: isEnabled(AuditLogInfo) → boolean
│   ├── AuditLogConfiguration     # Implements AuditLogCheck; filters by actor type + category + excludes
│   ├── AuditLogEntry             # Mutable domain model (~30 fields); factory: AuditLogEntry.of(Record)
│   ├── AuditLogInfo              # Immutable record; extracts category, entityType, operationType, actor, tenant from Record
│   └── transformers/             # Record-type-specific transformation logic
│       ├── AuditLogTransformer<R>        # Core interface: config(), create(), transform(), supports()
│       ├── AuditLogTransformerConfigs    # 25+ static TransformerConfig constants
│       ├── AuditLogTransformerRegistry   # Factory: partition-specific vs all-partition transformer lists
│       └── *AuditLogTransformer          # 25 concrete implementations (one per record type)
├── utils/
│   ├── ProcessCacheUtil          # Helpers for call activity/flow node lookups via cache
│   └── TreePathTruncator        # Intelligent segment-aware tree path truncation
└── historydeletion/
    └── HistoryDeletionConfiguration  # Cleanup scheduling parameters (delays, batch sizes)
```

## Key Abstractions

### ExporterEntityCache<K, T> (`cache/ExporterEntityCache.java`)
Generic cache interface with lazy loading from a backend. `ExporterEntityCacheImpl` wraps Caffeine's `LoadingCache` with `CaffeineCacheStatsCounter` for metrics. The `CacheLoaderFailedException` runtime exception wraps backend failures. Use `Optional<T> get(K)` — never assume cache hits.

### AuditLogTransformer<R extends RecordValue> (`auditlog/transformers/AuditLogTransformer.java`)
Core interface for transforming Zeebe `Record<R>` into `AuditLogEntry`. The `create()` default method calls `AuditLogEntry.of(record)` for base fields, then delegates to `transform()` for record-specific enrichment. If `transform()` does not set `result`, the default is `SUCCESS` for events and `FAIL` for `COMMAND_REJECTION` records.

### TransformerConfig (`AuditLogTransformer.TransformerConfig`)
Immutable record defining which records a transformer handles: `valueType`, `supportedIntents` (events), `supportedRejections` + `supportedRejectionTypes` (command rejections), and `dataCleanupIntents`. Use the builder chain: `TransformerConfig.with(valueType).withIntents(...).withRejections(...).withDataCleanupIntents(...)`.

### AuditLogInfo (`auditlog/AuditLogInfo.java`)
Extracted metadata from a Zeebe `Record`: operation category, entity type, operation type, actor, and tenant. Three static `Map` constants drive the mapping: `OPERATION_TYPE_MAP` (Intent → AuditLogOperationType, 50+ entries), `OPERATION_CATEGORY_MAP` (ValueType → category), `ENTITY_TYPE_MAP` (ValueType → entity type). Special case: `UserTaskIntent.ASSIGNED` maps to ASSIGN or UNASSIGN based on whether `assignee` is present.

### AuditLogTransformerRegistry (`auditlog/transformers/AuditLogTransformerRegistry.java`)
Factory providing two groups of transformers: **partition-specific** (15 transformers, run only on partition 1 for definitions/identity) and **all-partition** (10 transformers, run on every partition for instances). Use `Supplier<AuditLogTransformer<?>>` lists to create fresh instances per exporter.

## Data Flow

1. Exporter receives `Record<?>` from the stream processor
2. `AuditLogTransformer.supports(record)` checks `TransformerConfig` against record type + intent
3. `AuditLogTransformer.create(record)` builds base `AuditLogEntry` via `AuditLogEntry.of()` which extracts `AuditLogInfo` (actor, category, tenant from authorization headers and record metadata)
4. `transform(record, entry)` enriches with entity-specific fields (keys, IDs, descriptions)
5. `AuditLogConfiguration.isEnabled(info)` filters by actor type (USER/CLIENT/ANONYMOUS) × category × excludes
6. If `triggersCleanUp(record)` returns true, history deletion scheduling applies via `HistoryDeletionConfiguration`

## Extension Points

### Adding a New Audit Log Transformer
1. Define a `TransformerConfig` constant in `AuditLogTransformerConfigs` with the `ValueType`, supported intents, and optional rejection/cleanup intents
2. Add Intent → OperationType entries to `AuditLogInfo.OPERATION_TYPE_MAP`
3. Add ValueType → Category/EntityType entries to `AuditLogInfo.OPERATION_CATEGORY_MAP` and `ENTITY_TYPE_MAP`
4. Create a new class implementing `AuditLogTransformer<YourRecordValue>`, return the config from `config()`, and enrich fields in `transform()`
5. Register in `AuditLogTransformerRegistry` — add to `getSourcePartitionTransformerSuppliers()` (definition-level) or `getAllPartitionTransformerSuppliers()` (instance-level)
6. Add a test following the `*AuditLogTransformerTest` pattern using `ProtocolFactory` for record generation

### Adding a New Cached Entity Type
1. Create a record in a new subpackage under `cache/` (e.g., `cache/myentity/CachedMyEntity.java`)
2. Consumers instantiate `ExporterEntityCacheImpl<KeyType, CachedMyEntity>` with a `CacheLoader` that queries the backend

## Invariants

- Every `TransformerConfig` in `AuditLogTransformerConfigs` must have corresponding entries in `AuditLogInfo.OPERATION_TYPE_MAP` — `AuditLogTransformerConfigsTest` validates this via reflection
- `TransformerConfig` builder methods are immutable — each returns a new instance; do not chain `.withRejections()` after `.withRejections()` expecting accumulation (each call replaces the previous set)
- `AuditLogEntry.of()` sets base fields from the record; `transform()` must only set entity-specific fields. Never re-set `category`, `operationType`, or `actor` in `transform()` unless there is a domain-specific override (like `DecisionEvaluationAuditLogTransformer` setting FAIL result)
- Partition-specific transformers (definitions, identity) must only be registered in `getSourcePartitionTransformerSuppliers()`. Instance-level transformers go in `getAllPartitionTransformerSuppliers()`
- `ExporterEntityCacheImpl` wraps loader exceptions in `CacheLoaderFailedException` — callers must handle this runtime exception

## Common Pitfalls

- `TransformerConfig.withIntents().withRejections()` does NOT accumulate rejection types from a prior `withRejections()` call — the last call wins for each field. Chain explicitly: `.withRejections(intent, types...).withRejectionTypes(moreTypes...)`
- When two transformers handle the same `ValueType` (e.g., `GROUP_CONFIG` and `GROUP_ENTITY_CONFIG` both use `ValueType.GROUP`), they are distinguished by different supported intents. Both are separate transformer instances
- `AuditLogInfo.getOperationType()` has a special branch for `UserTaskIntent.ASSIGNED` — do not add it to `OPERATION_TYPE_MAP` (it would bypass the assignee-presence check)
- `ProcessCacheUtil.getCallActivityId()` performs bounds checking on `callActivityIndex` — see issue #42110. Always handle `Optional.empty()` returns
- `TreePathTruncator` supports two formats (prefixed `PI_/FN_/FNI_` and unprefixed numeric). Do not mix formats in a single path

## Testing Patterns

- Use `ProtocolFactory` (from `zeebe-protocol-test-util`) and `Immutable*.builder()` for record/value construction
- Test transformers with `@Nested` classes grouping happy-path, rejection, and cleanup scenarios
- `AuditLogTransformerConfigsTest` uses `@ParameterizedTest @MethodSource` with reflection to validate all static config fields — add new configs and they are automatically tested
- `AuditLogTransformerTest` tests the base interface contract (result defaulting, supports filtering)
- Run scoped: `./mvnw -pl zeebe/exporter-common -am test -DskipITs -DskipChecks -T1C`

## Key Reference Files

- `auditlog/transformers/AuditLogTransformer.java` — core transformer interface and `TransformerConfig` record
- `auditlog/AuditLogInfo.java` — static mapping tables (Intent→OperationType, ValueType→Category/EntityType)
- `auditlog/transformers/AuditLogTransformerRegistry.java` — partition-aware transformer factory
- `cache/ExporterEntityCache.java` — generic cache contract
- `auditlog/AuditLogConfiguration.java` — actor/category-based filtering logic