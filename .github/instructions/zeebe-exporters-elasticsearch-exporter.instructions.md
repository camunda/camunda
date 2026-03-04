```yaml
---
applyTo: "zeebe/exporters/elasticsearch-exporter/**"
---
```
# Elasticsearch Exporter — Module Instructions

## Purpose

The Elasticsearch exporter implements the `Exporter` SPI (`zeebe/exporter-api`) to stream Zeebe engine records into Elasticsearch indices. It is the primary bridge between the event-sourced engine and the secondary search storage consumed by Operate, Optimize, and Tasklist. The exporter handles record serialization, batched bulk indexing, index template/schema management, ILM retention policies, and record sequencing.

## Architecture

```
ElasticsearchExporter (Exporter SPI)
  ├── configure() → validates config, sets up DefaultRecordFilter, loads interceptor plugins
  ├── open()      → creates ES client, restores record counters from metadata, schedules flush
  ├── export()    → routes record → serializes → buffers in BulkIndexRequest → flushes when threshold met
  └── close()     → flushes remaining, updates position, closes client and plugins

Key collaborators:
  ElasticsearchExporterClient  — wraps ES Java client; bulk index, flush, template/ILM operations
  BulkIndexRequest             — in-memory buffer; serializes records with Jackson mixins
  RecordIndexRouter            — computes index name, document ID, routing, alias, search pattern
  TemplateReader               — reads JSON index/component templates from classpath resources
  ElasticsearchExporterSchemaManager — lazily creates templates and ILM policies per broker version
  ElasticsearchRecordCounters  — tracks per-ValueType counters for record sequencing
  RecordSequence               — encodes (partitionId, counter) into a single long sequence
  ElasticsearchMetrics         — Micrometer gauges/timers/counters for observability
```

## Data Flow

1. `ElasticsearchExporter.export(record)` checks `shouldExportRecord()` — filters by version and required value types.
2. `ElasticsearchExporterSchemaManager.createSchema()` ensures index templates exist for the broker version (created once, cached in `indexTemplatesCreated` set).
3. `RecordIndexRouter.indexFor(record)` computes the target index: `{prefix}_{valueType}_{version}_{dateSuffix}`.
4. `BulkIndexRequest.index()` serializes the record to `byte[]` using Jackson with version-aware mixins, appending a `sequence` attribute. Deduplicates consecutive identical actions.
5. `ElasticsearchExporterClient.shouldFlush()` triggers when `memoryLimit` or `size` thresholds are reached. A scheduled task also triggers periodic flushes every `bulk.delay` seconds.
6. `exportBulk()` splits the buffer into memory-limited chunks and sends each via the ES Bulk API.
7. On successful flush, `controller.updateLastExportedRecordPosition()` persists position and serialized record counters as metadata.

## Key Abstractions

- **`Exporter` interface** (`zeebe/exporter-api`): lifecycle methods `configure`, `open`, `export`, `close`, `purge`. The ES exporter implements all except `purge`.
- **`BulkIndexRequest`**: Pre-serializes records into `IndexOperation(BulkIndexAction, byte[])` pairs. Uses two `ObjectMapper` instances — `MAPPER` for current version, `PREVIOUS_VERSION_MAPPER` with extra `@JsonIgnoreProperties` mixins for backward compatibility with older broker versions.
- **`RecordSequence`**: Encodes `((long) partitionId << 51) + counter` to produce a globally unique, partition-aware sequence number per value type.
- **`ElasticsearchExporterConfiguration`**: Nested config classes — `IndexConfiguration` (per-value-type boolean flags, variable/process filters, shard/replica settings), `BulkConfiguration` (delay, size, memoryLimit), `RetentionConfiguration` (ILM), `AuthenticationConfiguration`, `ProxyConfiguration`.
- **`Template` record** (`dto/Template.java`): Immutable record with `MutableCopyBuilder` for safe template customization (patterns, aliases, composed_of, priority).

## Adding a New Value Type

When a new `ValueType` is added to the protocol:

1. Add a boolean field in `IndexConfiguration` (default `true` or `false` depending on whether it should be exported).
2. Add the case to `shouldIndexValueType()` in `ElasticsearchExporterConfiguration`.
3. If required for Optimize, add to `shouldIndexRequiredValueType()`.
4. Add a JSON index template at `src/main/resources/zeebe-record-{value-type}-template.json` following existing templates (compose with `zeebe-record` component template, define value mappings).
5. Add the `if (index.yourType)` block in `ElasticsearchExporterSchemaManager.createIndexTemplates()`.
6. Update `TestSupport.setIndexingForValueType()` and ensure the type is not in the `excludedValueTypes` set of `provideValueTypes()`.
7. If the new record value has fields that should not appear in older-version indices, add a Jackson mixin in `BulkIndexRequest`.

## Backward Compatibility (Version-Aware Serialization)

`BulkIndexRequest` maintains two `ObjectMapper` instances. Records from a previous minor version are serialized with `PREVIOUS_VERSION_MAPPER`, which applies extra `@JsonIgnoreProperties` mixins to strip fields unknown to older index templates (e.g., `rootProcessInstanceKey`). The check uses `isPreviousVersionRecord()` comparing the record's `brokerVersion` minor against the current exporter minor. Never remove existing mixins from `PREVIOUS_VERSION_MAPPER` — only add new ones when introducing fields in a new version.

## Configuration Validation Invariants

- Index prefix must not contain underscores (`_` is the index delimiter; aliases use `-`).
- `numberOfShards` ≥ 1, `numberOfReplicas` ≥ 0, `templatePriority` ≥ 0.
- `retention.minimumAge` must match `^[0-9]+[dhms]$` (days, hours, minutes, seconds).
- `indexSuffixDatePattern` must be a valid `DateTimeFormatter` pattern.
- `bulk.memoryLimit` > 100 MB triggers a warning (recommended 5–15 MB).

## Index Naming Convention

- **Index**: `{prefix}_{valueType}_{version}_{dateSuffix}` (delimiter: `_`). Example: `zeebe-record_process-instance_8.10.0_2026-03-04`.
- **Alias**: `{prefix}-{valueType}` (delimiter: `-`). Example: `zeebe-record-process-instance`.
- **Document ID**: `{partitionId}-{position}`. Example: `1-42`.
- **Routing**: `{partitionId}` — ensures all records from one partition land on the same shard.

## Testing Patterns

- **Unit tests** (`*Test.java`): Override `createClient()` to inject a Mockito mock of `ElasticsearchExporterClient`. Use `ExporterTestContext`, `ExporterTestController`, and `ImmutableRecord.builder()` from `zeebe-exporter-test`.
- **Integration tests** (`*IT.java`): Use `@Testcontainers` with `ElasticsearchContainer` from `TestSearchContainers`. The `TestClient` helper wraps the ES Java client for assertions (get document, verify templates).
- **Parameterized value type tests**: Use `TestSupport.provideValueTypes()` as a `@MethodSource` — it returns all exportable `ValueType` values excluding unsupported ones.
- **Test helper**: `TestSupport.setIndexingForValueType()` programmatically toggles config booleans by value type.
- Scoped test command: `./mvnw -pl zeebe/exporters/elasticsearch-exporter -am test -DskipITs -DskipChecks -Dtest=ElasticsearchExporterTest -T1C`
- Scoped IT command: `./mvnw -pl zeebe/exporters/elasticsearch-exporter -am verify -DskipChecks -Dtest=ElasticsearchExporterIT -T1C`

## Common Pitfalls

- Forgetting to update both `shouldIndexValueType()` AND `shouldIndexRequiredValueType()` when the new type is needed by Optimize.
- Forgetting to add the `if (index.X)` guard in `ElasticsearchExporterSchemaManager.createIndexTemplates()` — templates won't be created.
- Forgetting to update `TestSupport.setIndexingForValueType()` — parameterized tests will throw `IllegalArgumentException`.
- Adding a field to a record value without a mixin in `PREVIOUS_VERSION_MAPPER` — causes mapping exceptions when exporting records from an older broker version.
- Using `_` in index prefix — breaks the delimiter convention and causes `ExporterException`.
- Modifying `PREVIOUS_VERSION_MAPPER` mixins for released versions — frozen after release.

## Key Files

| File | Role |
|------|------|
| `ElasticsearchExporter.java` | SPI implementation; lifecycle, validation, flush scheduling |
| `BulkIndexRequest.java` | Record buffer with version-aware Jackson serialization and mixins |
| `ElasticsearchExporterClient.java` | ES Bulk API calls, template/ILM management, chunked flush |
| `ElasticsearchExporterConfiguration.java` | All config: index flags, bulk, retention, auth, proxy, filters |
| `RecordIndexRouter.java` | Index/alias/ID/routing computation |
| `ElasticsearchExporterSchemaManager.java` | Lazy template and ILM creation per broker version |
| `TemplateReader.java` | Reads and customizes JSON templates from classpath |
| `RecordSequence.java` | Partition-aware sequence encoding for range queries |
| `src/main/resources/zeebe-record-template.json` | Shared component template (common field mappings) |
| `TestSupport.java` | Test utilities: value type toggling, parameterized sources |