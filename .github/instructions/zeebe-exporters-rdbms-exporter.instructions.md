```yaml
---
applyTo: "zeebe/exporters/rdbms-exporter/**"
---
```
# RDBMS Exporter Module

## Purpose

The RDBMS exporter reads committed engine records from the Zeebe log stream and writes them to a relational database (PostgreSQL, MariaDB, MySQL, MSSQL, Oracle) via the `camunda-db-rdbms` module. It bridges the event-sourced engine with the CQRS read side, making process data queryable by Operate, Tasklist, and the REST/MCP APIs. It implements the `io.camunda.zeebe.exporter.api.Exporter` SPI.

## Architecture

```
RdbmsExporterFactory (SPI entry point)
  └─ RdbmsExporterWrapper (implements Exporter — configure/open/close/export/purge)
       └─ RdbmsExporter (core logic — handler dispatch, batched flushing, position tracking)
            ├─ Map<ValueType, List<RdbmsExportHandler>> (handler registry)
            ├─ RdbmsWriters (batched write queue from camunda-db-rdbms)
            ├─ HistoryCleanupService (TTL-based cleanup scheduling)
            └─ HistoryDeletionService (explicit deletion scheduling)
```

### Data Flow

1. `RdbmsExporterFactory.newInstance()` creates `RdbmsExporterWrapper` with `RdbmsService`, `RdbmsSchemaManager`, `VendorDatabaseProperties`.
2. `configure()` reads `ExporterConfiguration`, creates `RdbmsWriters`, instantiates entity caches (process, decision requirements, batch operation), and registers all `RdbmsExportHandler` instances keyed by `ValueType`.
3. `export(Record)` dispatches to handlers matching `record.getValueType()`, calling `canExport()` then `export()`. After all handlers process a record, triggers a flush check.
4. Flushing is batched: records queue in `ExecutionQueue` and flush either on interval (`flushInterval`, default 500ms), on queue size threshold, or forced on close. Pre-flush listener updates position in RDBMS; post-flush listener updates position in broker and records latency metrics.

## Key Abstractions

- **`RdbmsExportHandler<T extends RecordValue>`** (`RdbmsExportHandler.java`): Core interface. Two methods: `canExport(Record<T>)` filters by intent/element type; `export(Record<T>)` maps and writes to DB via a typed `Writer`.
- **`RdbmsExporter`** (`RdbmsExporter.java`): Orchestrates handler dispatch, flush lifecycle, position tracking, and scheduled cleanup tasks. Constructed via `Builder`.
- **`RdbmsExporterWrapper`** (`RdbmsExporterWrapper.java`): Implements `Exporter` SPI. Handles configuration parsing, handler registration, cache setup, and delegates to `RdbmsExporter`.
- **`ExporterConfiguration`** (`ExporterConfiguration.java`): POJO deserialized from exporter config. Contains flush interval, queue size, cache sizes, history TTLs, insert batching, audit log config. Has `validate()` method throwing `ExporterException` on invalid values.
- **`RdbmsExporterFactory`** (`RdbmsExporterFactory.java`): `ExporterFactory` implementation with `exporterId()` = `"rdbms"`.

## Handler Patterns

### Standard Entity Handler

Each handler follows this pattern — see `AuthorizationExportHandler.java`, `VariableExportHandler.java`:
1. Implement `RdbmsExportHandler<SpecificRecordValue>`.
2. Accept the relevant `Writer` (from `camunda-db-rdbms`) in constructor.
3. `canExport()`: filter by `ValueType` and specific `Intent` values (e.g., `CREATED`, `UPDATED`, `DELETED`).
4. `export()`: switch on intent → call `writer.create()`, `writer.update()`, `writer.delete()`, etc.
5. Private `map()` method converts `RecordValue` → `DbModel` using the corresponding builder.

### Handlers Requiring Cache

Handlers like `ProcessInstanceExportHandler`, `FlowNodeExportHandler`, `IncidentExportHandler`, `UserTaskExportHandler` accept an `ExporterEntityCache<Long, CachedProcessEntity>` to resolve process definitions (flow node names, call activity IDs). Use `ProcessCacheUtil` for lookups.

### Abstract Handler

`AbstractCorrelatedMessageSubscriptionExportHandler<T>` provides a template: subclasses implement `mapValue()` to populate builder fields specific to their record type.

### Batch Operation Status Handlers

`RdbmsBatchOperationStatusExportHandler<T>` is an abstract base for tracking batch operation item completion. Subclasses override `isCompleted()`, `isFailed()`, `getItemKey()`, `getProcessInstanceKey()`, `getRootProcessInstanceKey()`. Uses `batchOperationCache` to verify `relevantOperationType` matches. See `ProcessInstanceCancellationBatchOperationExportHandler.java`.

### Audit Log Handler

`AuditLogExportHandler<R>` is generic over `RecordValue`, delegating to `AuditLogTransformer<R>` from `zeebe/exporter-common`. Registered once per transformer type. Enabled via `ExporterConfiguration.auditLog`.

## Partition-Aware Registration

Certain handlers register **only on partition 1** (constant `PROCESS_DEFINITION_PARTITION = 1`): `PROCESS`, `MAPPING_RULE`, `TENANT`, `ROLE`, `USER`, `AUTHORIZATION`, `DECISION`, `DECISION_REQUIREMENTS`, `FORM`, plus partition-specific audit log transformers. All other handlers register on every partition. See `RdbmsExporterWrapper.createHandlers()`.

## Caches

Three Caffeine-backed caches, each backed by an RDBMS-specific `CacheLoader`:
- **`processCache`** (`RdbmsProcessCacheLoader`): keyed by `processDefinitionKey`, loads `CachedProcessEntity` with flow node names and call activity IDs.
- **`decisionRequirementsCache`** (`RdbmsDecisionRequirementsCacheLoader`): keyed by `decisionRequirementsKey`.
- **`batchOperationCache`** (`RdbmsBatchOperationCacheLoader`): keyed by batch operation key string.

Cache sizes are configurable via `ExporterConfiguration.CacheConfiguration.maxSize` (default 10,000).

## Utilities

- **`TreePath`** (`utils/TreePath.java`): Builds hierarchical call-chain paths (`PI_<key>/FN_<id>/FNI_<key>/...`) for process instances and incidents. Truncates to vendor column size limit via `TreePathTruncator`.
- **`ExportUtil`** (`utils/ExportUtil.java`): `buildTreePath()` for flow node instances; `tenantOrDefault()` for tenant fallback.
- **`DateUtil`** (`utils/DateUtil.java`): Converts timestamps (`Long`, `String`, `Instant`) to `OffsetDateTime` in UTC.

## Extension: Adding a New Handler

1. Create a handler class in `handlers/` implementing `RdbmsExportHandler<YourRecordValue>`.
2. Accept the relevant `Writer` from `camunda-db-rdbms` in the constructor. If the handler needs process cache data, also accept `ExporterEntityCache<Long, CachedProcessEntity>`.
3. Implement `canExport()` filtering by `ValueType` and specific `Intent` enum values.
4. Implement `export()` with intent-based dispatch to writer methods.
5. Register the handler in `RdbmsExporterWrapper.createHandlers()` via `builder.withHandler(ValueType.YOUR_TYPE, new YourHandler(...))`. Place it inside the `partitionId == PROCESS_DEFINITION_PARTITION` block if it should only run on partition 1.
6. Multiple handlers can share the same `ValueType` — each is evaluated independently.

## Invariants

- Never mutate `RdbmsExporter` state from handler code — handlers only call writer methods.
- Position is updated in RDBMS (pre-flush) and broker (post-flush) atomically with the batch. Never update position outside the flush lifecycle.
- Schema must be initialized (`rdbmsSchemaManager.isInitialized()`) before the exporter opens; otherwise `ExporterException` is thrown.
- When `flushInterval` is zero or `queueSize` is ≤ 0, the exporter forces flush after every exported record (no batching).
- Scheduled cleanup/deletion tasks always reschedule themselves even on failure to ensure continued operation.

## Common Pitfalls

- Forgetting to register a new handler in `RdbmsExporterWrapper.createHandlers()` — the handler won't be called.
- Registering a partition-1-only handler outside the `if (partitionId == PROCESS_DEFINITION_PARTITION)` block causes duplicate writes across partitions.
- Using the wrong `DateUtil` — this module has its own `utils.DateUtil` and also uses `io.camunda.zeebe.util.DateUtil`. The module-local one handles `Instant` and `String` parsing; the zeebe-util one handles `Long` timestamps.
- Not truncating `TreePath` to the vendor column size limit causes DB constraint violations.
- Batch operation status handlers must check `isRelevantForBatchOperation()` via cache to avoid reacting to unrelated follow-up records with the same `batchOperationReference`.

## Key Files

| File | Role |
|------|------|
| `RdbmsExporter.java` | Core export/flush/position lifecycle |
| `RdbmsExporterWrapper.java` | SPI adapter, handler registration, cache setup |
| `RdbmsExportHandler.java` | Handler interface (`canExport` + `export`) |
| `ExporterConfiguration.java` | Configuration POJO with validation |
| `handlers/ProcessInstanceExportHandler.java` | Complex handler example with cache + tree path |
| `handlers/batchoperation/RdbmsBatchOperationStatusExportHandler.java` | Abstract base for batch operation tracking |
| `RdbmsExporterTest.java` | Tests for flush, position, scheduling lifecycle |
| `RdbmsExporterWrapperTest.java` | Tests for handler registration and configuration |

## Build & Test

```bash
# Unit tests only
./mvnw -pl zeebe/exporters/rdbms-exporter -am test -DskipITs -DskipChecks -T1C

# Single test class
./mvnw -pl zeebe/exporters/rdbms-exporter -am test -DskipITs -DskipChecks -Dtest=RdbmsExporterTest -T1C

# Format before committing
./mvnw -pl zeebe/exporters/rdbms-exporter spotless:apply -T1C
```