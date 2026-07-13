```yaml
---
applyTo: "zeebe/exporters/camunda-exporter/**"
---
```
# Camunda Exporter Module

## Purpose

The camunda-exporter transforms Zeebe engine records into search-engine-compatible entities and persists them to Elasticsearch or OpenSearch. It implements the `Exporter` SPI (`zeebe/exporter-api`) and is the primary bridge between the event-sourced engine and the secondary storage that powers Operate, Tasklist, and the REST search APIs. It also runs background tasks for archiving, incident post-processing, batch operation updates, and history deletion.

## Architecture

```
CamundaExporter (Exporter SPI)
  ├── configure() → validates config, sets RecordFilter
  ├── open()      → creates ClientAdapter, SchemaManager, ExporterBatchWriter, BackgroundTaskManager
  ├── export()    → delegates to ExporterBatchWriter.addRecord(), flushes on size/memory/time thresholds
  ├── close()     → flushes remaining, closes adapters and tasks
  └── purge()     → truncates all indices

ExporterBatchWriter
  ├── dispatches Record to matching ExportHandler(s) by ValueType
  ├── caches entities by (id, entityType), deduplicating within a batch
  └── flush() → calls handler.flush() for each entity, then BatchRequest.execute()

ExportHandler<T, R>   (core abstraction — ~60 implementations)
  ├── handlesRecord()  → intent-based filtering
  ├── generateIds()    → entity ID derivation
  ├── createNewEntity() / updateEntity() → record-to-entity mapping
  └── flush()          → add/upsert/update/delete on BatchRequest

ClientAdapter          (factory for backend-specific clients)
  ├── ElasticsearchAdapter
  └── OpensearchAdapter

BackgroundTaskManager  (ScheduledThreadPoolExecutor)
  ├── ArchiverJob variants (process instance, batch operation, usage metrics, decisions, audit log)
  ├── IncidentUpdateTask (post-import incident enrichment)
  ├── BatchOperationUpdateTask
  ├── HistoryDeletionJob
  └── ReschedulingTask (exponential backoff wrapper)
```

## Key Abstractions

- **`ExportHandler<T extends ExporterEntity, R extends RecordValue>`** — Central interface at `handlers/ExportHandler.java`. Each handler maps one `ValueType` + specific intents to one entity type and target index. Handlers are stateless; entity state is managed by `ExporterBatchWriter`.
- **`ExporterBatchWriter`** — At `store/ExporterBatchWriter.java`. Accumulates entities across records within a batch, deduplicates by `(entityId, entityType)`, preserves flush ordering via `LinkedHashMap`, and delegates persistence to `BatchRequest`.
- **`BatchRequest`** — At `store/BatchRequest.java`. Abstraction over ES/OS bulk API. Supports `add`, `upsert`, `upsertWithScript`, `update`, `delete` with routing. Implementations: `ElasticsearchBatchRequest`, `OpensearchBatchRequest`.
- **`ClientAdapter`** — At `adapters/ClientAdapter.java`. Factory interface with static `of()` method that selects ES or OS adapter based on `ConnectConfiguration.getTypeEnum()`.
- **`ExporterResourceProvider`** — At `ExporterResourceProvider.java`. Provides index descriptors, export handlers, caches, and custom error handlers. `DefaultExporterResourceProvider` is the production implementation that wires ~60 handlers.
- **`BackgroundTask`** — At `tasks/BackgroundTask.java`. Async task returning `CompletionStage<Integer>`. Wrapped by `ReschedulingTask` which applies exponential backoff on idle/error.
- **`ExporterMetadata`** — At `ExporterMetadata.java`. Serialized to/from the exporter's persistent metadata store. Tracks `lastIncidentUpdatePosition` (monotonically increasing), `firstUserTaskKeys`, and `firstProcessMessageSubscriptionKey`.

## Data Flow

1. `CamundaExporter.export(Record)` receives a record from the stream processor.
2. `ExporterBatchWriter.addRecord()` looks up handlers for the record's `ValueType`, filters by `handlesRecord()`, generates IDs, and calls `updateEntity()` on cached or newly created entities.
3. Flush triggers when batch size ≥ `bulk.size` (default 5000), memory ≥ `bulk.memoryLimit` (default 20MB), or time ≥ `bulk.delay` (default 1s).
4. On flush, each handler's `flush()` writes to `BatchRequest`, then `BatchRequest.execute()` sends the bulk request to ES/OS.
5. After successful flush, `controller.updateLastExportedRecordPosition()` advances the exporter position with serialized metadata.

## Adding a New Export Handler

1. Create a class implementing `ExportHandler<YourEntity, YourRecordValue>` in `handlers/`.
2. Implement all 7 methods: `getHandledValueType()`, `getEntityType()`, `handlesRecord()`, `generateIds()`, `createNewEntity()`, `updateEntity()`, `flush()`, `getIndexName()`.
3. Register the handler in `DefaultExporterResourceProvider.init()` by adding it to the `exportHandlers` set with the appropriate index name from `indexDescriptors.get(YourTemplate.class).getFullQualifiedName()`.
4. If the handler needs a cache (process, form, decision requirements, batch operation), inject it via constructor from the caches created in `DefaultExporterResourceProvider.init()`.
5. Add the new `ValueType` to `CamundaExporterRecordFilter.VALUE_TYPES_2_EXPORT` if not already present.
6. Add a corresponding unit test in `src/test/java/.../handlers/YourHandlerTest.java`.

## Adding a New Background Task

1. Implement `BackgroundTask` interface — return `CompletionStage<Integer>` from `execute()`.
2. Create a repository interface in the appropriate `tasks/` subdirectory with ES and OS implementations.
3. Wire the repository in `BackgroundTaskManagerFactory` (both ES and OS branches).
4. Build a `ReschedulingTask` wrapping your task in `BackgroundTaskManagerFactory.buildTasks()`.
5. Partition-specific tasks (like batch operation updates) should be gated with `if (partitionId == START_PARTITION_ID)`.

## Error Handling

- `ErrorHandler` functional interface at `errorhandling/ErrorHandler.java` with `ErrorHandlers` enum providing `IGNORE_DOCUMENT_DOES_NOT_EXIST` and `THROWING` strategies.
- Custom error handlers are registered per index in `DefaultExporterResourceProvider` — currently `OperationTemplate`, `BatchOperationTemplate`, and `ListViewTemplate` ignore 404/missing document errors.
- `PersistenceException` wraps all ES/OS bulk errors; `ExporterException` wraps `PersistenceException` in the flush path, causing the exporter to retry.
- Background tasks use `ReschedulingTask` with exponential backoff on errors (up to 10s max delay).

## Caching

Four Caffeine-backed caches are initialized in `DefaultExporterResourceProvider.init()`:
- **processCache** — `CachedProcessEntity` by process definition key (used by ~8 handlers for call activity resolution, process names)
- **decisionRequirementsCache** — `CachedDecisionRequirementsEntity` by key
- **formCache** — `CachedFormEntity` by composite key
- **batchOperationCache** — `CachedBatchOperationEntity` by batch operation ID

Each cache has backend-specific `CacheLoader` implementations (ES/OS) in `cache/` subdirectories. Cache sizes are configurable via `ExporterConfiguration.CacheConfiguration.maxCacheSize` (default 10000).

## Configuration

`ExporterConfiguration` at `config/ExporterConfiguration.java` contains:
- `connect` — ES/OS connection settings (type, URL, index prefix)
- `bulk` — flush thresholds: `size` (5000), `delay` (1s), `memoryLimit` (20MB)
- `history` — archiver settings: rollover interval, batch size, retention, wait period
- `processCache`, `formCache`, `decisionRequirementsCache`, `batchOperationCache` — cache sizes
- `postExport` — incident/batch operation post-processing: batch size, delay, ignoreMissingData
- `auditLog` — audit log feature toggle and configuration
- `historyDeletion` — history deletion scheduling
- `createSchema` — whether to create/validate schema on open (default true)

`ConfigValidator` validates all configuration at `configure()` time before the exporter opens.

## Testing Patterns

- **Handler unit tests**: Use `ProtocolFactory` to generate test records with specific intents/values. Mock `BatchRequest` to verify flush calls. Use `TestProcessCache` for cache-dependent handlers. Follow `// given`, `// when`, `// then` structure. See `IncidentHandlerTest.java`.
- **Integration tests (`CamundaExporterIT`)**: Use `@TestTemplate` with `CamundaExporterITTemplateExtension` for ES/OS variants. Uses `ExporterTestContext`/`ExporterTestController` from `zeebe/exporter-test`. Testcontainers for ES/OS instances.
- **Scoped build**: `./mvnw -pl zeebe/exporters/camunda-exporter -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`
- **Integration tests**: `./mvnw -pl zeebe/exporters/camunda-exporter -am verify -DskipUTs -DskipChecks -T1C`

## Common Pitfalls

- Never mutate an `ExportHandler` — handlers are shared across all records in a batch. All state lives in the entity objects managed by `ExporterBatchWriter`.
- Always add new `ValueType`s to both the handler AND `CamundaExporterRecordFilter.VALUE_TYPES_2_EXPORT`; missing the filter means records are silently dropped.
- When multiple handlers write to the same index+id, `ExporterBatchWriter` preserves ordering via `LinkedHashMap` — do not rely on handler registration order for correctness.
- `ExporterMetadata.setLastIncidentUpdatePosition()` is monotonically increasing via `AtomicLongFieldUpdater` — never call with a smaller value.
- Background task repositories must be created for BOTH ES and OS in `BackgroundTaskManagerFactory.build()`.
- Caches are populated eagerly by handlers (e.g., `ProcessHandler` puts into `processCache` on `updateEntity`), not just loaded on-demand.

## Key Reference Files

- `CamundaExporter.java` — Exporter lifecycle, flush logic, record filter
- `handlers/ExportHandler.java` — Core handler interface contract
- `store/ExporterBatchWriter.java` — Batching, deduplication, and flush orchestration
- `DefaultExporterResourceProvider.java` — Handler and cache wiring (~60 handlers registered)
- `tasks/BackgroundTaskManagerFactory.java` — Background task construction and repository wiring
- `config/ExporterConfiguration.java` — All configuration options and defaults