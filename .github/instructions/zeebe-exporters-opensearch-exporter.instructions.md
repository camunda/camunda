```yaml
---
applyTo: "zeebe/exporters/opensearch-exporter/**"
---
```
# OpenSearch Exporter Module

## Purpose

The OpenSearch exporter is a Zeebe exporter plugin that streams engine records from the broker's log into OpenSearch indices. It implements the `io.camunda.zeebe.exporter.api.Exporter` SPI to receive committed records, serialize them as JSON documents, batch them via bulk requests, and manage OpenSearch index templates and ISM (Index State Management) retention policies. It is the OpenSearch counterpart to the Elasticsearch exporter.

## Architecture

```
OpensearchExporter (Exporter SPI entry point)
  ├── OpensearchExporterConfiguration (FilterConfiguration implementation)
  │     ├── IndexConfiguration — per-ValueType toggle booleans, shard/replica/priority settings
  │     ├── BulkConfiguration — delay, size, memoryLimit thresholds
  │     ├── RetentionConfiguration — ISM policy name, minimumAge, enabled flag
  │     ├── AwsConfiguration — AWS region, service name, enabled flag
  │     ├── AuthenticationConfiguration, SecurityConfiguration, ProxyConfiguration
  │     └── interceptorPlugins — PluginRepository for HTTP interceptors
  ├── OpensearchClient (wraps opensearch-java OpenSearchClient)
  │     ├── BulkIndexRequest — in-memory buffer of serialized BulkOperation objects
  │     ├── RecordIndexRouter — computes index name, document ID, routing, aliases
  │     ├── TemplateReader — reads JSON index templates from classpath resources
  │     └── OpensearchMetrics — Micrometer gauges/counters/timers
  ├── OpensearchConnector — builds OpenSearchClient (default or AWS AwsSdk2Transport)
  ├── OpensearchExporterSchemaManager — lazy index template creation, ISM policy lifecycle
  ├── OpensearchRecordCounters — per-ValueType sequence counters persisted in exporter metadata
  └── dto/ — BulkIndexAction, Template, GetIndexStateManagementPolicyResponse records
```

### Data Flow

1. Broker's `ExporterDirector` calls `configure()` → exporter reads config, sets `DefaultRecordFilter`, loads interceptor plugins.
2. `open()` → creates `OpensearchClient` via `OpensearchConnector`, restores `OpensearchRecordCounters` from persisted metadata, schedules periodic flush task.
3. `export(record)` → `shouldExportRecord()` checks version-based filtering (8.8+ only required types unless `includeEnabledRecords`). If accepted: `schemaManager.createSchema()` (lazy, once per broker version), `client.index()` adds to `BulkIndexRequest` buffer, updates `lastPosition`. If `shouldFlush()`, calls `flush()` → `exportBulk()` sends `BulkRequest` to OpenSearch, then updates controller position + metadata.
4. `close()` → flushes remaining records, updates position, closes client and plugin repository.

## Key Abstractions

- **`Exporter` SPI** (`zeebe/exporter-api`): Lifecycle interface (`configure`, `open`, `export`, `close`). The exporter must be stateless across restarts — state is recovered via `Controller.readMetadata()`.
- **`FilterConfiguration`** (`zeebe/exporter-filter`): Implemented by `OpensearchExporterConfiguration` to control which `ValueType` and `RecordType` combinations are indexed. `DefaultRecordFilter` wraps it for the engine.
- **`BulkIndexRequest`**: Serializes records to JSON bytes eagerly (not on flush), buffers `BulkOperation` objects. Uses Jackson `ObjectMapper` with version-specific mixins (`MAPPER` for current version, `PREVIOUS_VERSION_MAPPER` for older broker versions that lack new fields).
- **`RecordIndexRouter`**: Computes index name as `{prefix}_{valueType}_{version}_{date}`, document ID as `{partitionId}-{position}`, routing as `{partitionId}`.
- **`RecordSequence`**: Record `(partitionId, counter)` → `sequence = (partitionId << 51) + counter`. Appended to each document via `@JsonAppend` mixin. Used for range queries when reading indices.
- **`OpensearchExporterSchemaManager`**: Creates component template + per-ValueType index templates on first record of each broker version. Manages ISM policy create/update/delete based on `RetentionConfiguration`.
- **`GetIndexStateManagementPolicyResponse`**: Custom deserializer record wrapping the ISM API response (works around `lastUpdatedTime` long overflow in the OpenSearch Java client).

## Adding a New ValueType

When the engine introduces a new `ValueType`:

1. Add a boolean field to `IndexConfiguration` in `OpensearchExporterConfiguration.java` (default `true` or `false` depending on whether it should be exported by default).
2. Add the corresponding case to `shouldIndexValueType()` in the same file.
3. If this type is required for Optimize/Analytics, also add to `shouldIndexRequiredValueType()`.
4. Add a `createValueIndexTemplate()` call in `OpensearchExporterSchemaManager.createIndexTemplates()`.
5. Create a JSON template file at `src/main/resources/zeebe-record-{value-type}-template.json` (kebab-case).
6. Add the type to `TestSupport.setIndexingForValueType()` switch statement.
7. If the type should be tested, ensure it is NOT in `TestSupport.provideValueTypes()` exclusion set.

## Configuration Conventions

- Index prefix must not contain underscores (validated in `OpensearchExporter.validate()`).
- `numberOfShards` must be ≥ 1, `numberOfReplicas` ≥ 0, `priority` ≥ 0.
- Bulk memory limit above 100MB triggers a warning. Default bulk size is 1000, memory limit 10MB, delay 5s.
- Authentication credentials are never logged (`AuthenticationConfiguration.toString()` redacts them).
- AWS transport is auto-detected via `AwsCredentialsProvider.resolveCredentials()` when `aws.enabled = true`.

## Version-Aware Serialization

`BulkIndexRequest` uses two `ObjectMapper` instances. Records from previous minor versions use `PREVIOUS_VERSION_MAPPER` with extra `@JsonIgnoreProperties` mixins to exclude fields that did not exist in older versions (e.g., `rootProcessInstanceKey`, `moveInstructions`). Use `isPreviousVersionRecord()` to determine which mapper applies. When adding new record fields:
- Add `@JsonIgnoreProperties` mixins to `PREVIOUS_VERSION_MAPPER` if old-version indices lack the field.
- Keep `MAPPER` (current version) clean — it only strips `authorizations` and `agent`.

## Testing Patterns

- **Unit tests** (`*Test`): Use `ExporterTestContext`, `ExporterTestController`, and `ExporterTestConfiguration` from `zeebe/exporter-test`. Mock `OpensearchClient` via overriding `createClient()`. Use `ImmutableRecord.builder()` for test records.
- **Integration tests** (`*IT`): Use `@Testcontainers` with `TestSearchContainers.createDefaultOpensearchContainer()`. Use `TestClient` to query OpenSearch directly. Use `ProtocolFactory` to generate randomized records. Use Awaitility for async ISM policy assertions.
- **`TestSupport`**: Central utility for toggling `IndexConfiguration` fields programmatically and providing `ValueType` streams for parameterized tests.
- Run scoped: `./mvnw -pl zeebe/exporters/opensearch-exporter -am test -DskipITs -DskipChecks -Dtest=OpensearchExporterTest -T1C`
- Run ITs: `./mvnw -pl zeebe/exporters/opensearch-exporter -am verify -DskipUTs -DskipChecks -T1C` (requires Docker for Testcontainers)

## Common Pitfalls

- Never use underscore in `index.prefix` — it will throw `ExporterException` during `configure()`.
- Always update `TestSupport.setIndexingForValueType()` when adding value types, or parameterized tests will fail with `IllegalArgumentException`.
- The `BulkIndexRequest` deduplicates by `BulkIndexAction` — if the same action is indexed twice consecutively, the second is silently dropped (retry safety).
- `OpensearchRecordCounters` are only updated after successful `client.index()` — if indexing throws, the counter is not incremented, preserving retry idempotency.
- ISM policy comparison (`equalsConfiguration()`) has custom deserialization due to OpenSearch Java client bugs with `lastUpdatedTime` overflow — do not use the default `Policy._DESERIALIZER`.
- Template JSON files in `src/main/resources/` must follow the naming pattern `zeebe-record-{value-type}-template.json` (kebab-case of `ValueType.name()`).

## Key Files

| File | Role |
|------|------|
| `OpensearchExporter.java` | SPI entry point: lifecycle, export loop, flush scheduling |
| `OpensearchClient.java` | Wraps `OpenSearchClient`: bulk indexing, template management, ISM policy CRUD |
| `BulkIndexRequest.java` | Serialization buffer with version-aware Jackson mixins |
| `OpensearchExporterConfiguration.java` | Full configuration model implementing `FilterConfiguration` |
| `RecordIndexRouter.java` | Index naming, document ID, routing computation |
| `OpensearchExporterSchemaManager.java` | Lazy schema creation, ISM policy lifecycle |
| `OpensearchConnector.java` | HTTP/AWS transport builder with auth, SSL, proxy support |
| `TestSupport.java` | Test utility for value type configuration and parameterized test data |