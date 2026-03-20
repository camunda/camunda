```yaml
---
applyTo: "zeebe/exporter-api/**"
---
```
# Zeebe Exporter API — Module Instructions

## Purpose

This module defines the **SPI contract** for Zeebe record exporters. It is a minimal, stable, Apache-2.0-licensed API that external and internal exporters implement to receive committed engine records from the broker's log stream. The API is intentionally small (6 source files) and guards backward compatibility via Revapi checks.

## Architecture

The module contains only interfaces and one exception class — no implementations. The broker provides all implementations via `ExporterContainer` (implements `Controller`) and `ExporterContext` (implements `Context`) in `zeebe/broker/src/main/java/.../exporter/`.

```
Exporter (SPI)
  ├── configure(Context)    ← called twice: once for validation at startup, once before open
  ├── open(Controller)      ← allocate resources, read metadata, schedule tasks
  ├── export(Record<?>)     ← process each committed record
  ├── close()               ← tear down resources
  └── purge()               ← delete all exported data (cluster purge)

Context (read-only config)
  ├── getConfiguration()    → Configuration (id + arguments map + instantiate)
  ├── getPartitionId()      → int
  ├── getLogger()           → SLF4J Logger
  ├── getMeterRegistry()    → Micrometer MeterRegistry
  ├── clock()               → InstantSource (controllable time)
  └── setFilter(RecordFilter) ← two-phase filtering (metadata then full record)

Controller (runtime control)
  ├── updateLastExportedRecordPosition(long)             ← ack position
  ├── updateLastExportedRecordPosition(long, byte[])     ← ack position + metadata
  ├── getLastExportedRecordPosition()                    ← read current position
  ├── scheduleCancellableTask(Duration, Runnable)         → ScheduledTask
  └── readMetadata()                                      → Optional<byte[]>
```

## Key Abstractions

- **`Exporter`** (`Exporter.java`): Main SPI interface. Must have a no-arg constructor. All methods except `export()` have default (no-op) implementations. The `export()` method is retried indefinitely on `RuntimeException`.
- **`Context`** (`context/Context.java`): Read-only exporter configuration and partition context, provided during `configure()`. Use `setFilter()` to apply a `RecordFilter` limiting which records reach `export()`.
- **`Context.RecordFilter`** (inner interface): Two-phase filtering — `acceptType`/`acceptValue`/`acceptIntent` run on metadata before deserialization; `acceptRecord` runs on fully deserialized records.
- **`Controller`** (`context/Controller.java`): Runtime handle provided during `open()`. Used to acknowledge exported positions, persist arbitrary metadata, schedule delayed tasks, and read back metadata.
- **`Configuration`** (`context/Configuration.java`): Wraps the exporter's YAML arguments. Use `instantiate(Class)` to map arguments to a typed config POJO.
- **`ScheduledTask`** (`context/ScheduledTask.java`): Handle for cancelling scheduled tasks.
- **`ExporterException`** (`ExporterException.java`): Standard `RuntimeException` for exporter errors.

## Dependencies

This module depends on only two runtime libraries:
- `zeebe-protocol` — provides `Record`, `RecordType`, `ValueType`, `Intent` interfaces
- `micrometer-core` — provides `MeterRegistry` for metrics instrumentation
- `slf4j-api` — logging facade

Never add heavyweight dependencies (Spring, Jackson, database drivers) to this module. It is a public API consumed by third-party exporter authors.

## Backward Compatibility

- **Revapi** is configured in `revapi.json` to enforce API compatibility against the last released version.
- Adding methods to `Controller` or `Context` is explicitly allowed (justified in `revapi.json`) because exporters only _consume_ these interfaces; the broker provides implementations.
- Adding abstract methods to `Exporter` or `RecordFilter` **breaks compatibility**. Always provide default implementations.
- The `revapi-maven-plugin` runs as part of the build. Check `revapi.json` before adding any new methods to understand what's allowed.

## Exporter Lifecycle

1. **Instantiation**: Broker creates exporter via no-arg constructor (through `ExporterFactory` in `zeebe/broker/.../exporter/repo/`).
2. **`configure(Context)`**: Called at startup for validation (instance is discarded), then again before `open()`. Set filters here, validate config, fail-fast with exceptions.
3. **`open(Controller)`**: Called once per partition lifecycle. Allocate resources, restore metadata via `controller.readMetadata()`, schedule periodic flush tasks.
4. **`export(Record<?>)`**: Called for each filtered record. Acknowledge via `controller.updateLastExportedRecordPosition()` after durable export. The underlying `Record` buffer is reused — call `record.toJson()` or `record.clone()` to retain data.
5. **`close()`**: Called once. Flush pending data and release resources.
6. **`purge()`**: Called during cluster purge. Must be idempotent and blocking.

## Record Filtering

Set a `RecordFilter` during `configure()` to reduce the volume of records reaching `export()`:
- **Phase 1** (metadata, no deserialization): `acceptType(RecordType)`, `acceptValue(ValueType)`, `acceptIntent(Intent)`
- **Phase 2** (full record): `acceptRecord(Record<?>)` — slower but richer data access
- Default implementations accept everything. Override only what you need.

## Position Acknowledgment and Metadata

- Call `controller.updateLastExportedRecordPosition(position)` only after records are durably persisted. The broker uses acknowledged positions for log compaction.
- Use the `byte[] metadata` overload to persist exporter-specific state (e.g., record counters, schema versions). Metadata is stored only when the position advances.
- Read metadata back with `controller.readMetadata()` during `open()`.

## Concrete Implementations (for reference)

| Exporter | Location | Pattern |
|----------|----------|---------|
| `ElasticsearchExporter` | `zeebe/exporters/elasticsearch-exporter/` | Full lifecycle: config validation, filter, bulk flush, metadata persistence |
| `OpensearchExporter` | `zeebe/exporters/opensearch-exporter/` | Mirrors ES exporter structure |
| `CamundaExporter` | `zeebe/exporters/camunda-exporter/` | Unified exporter for ES/OS with handler dispatch |
| `RdbmsExporterWrapper` | `zeebe/exporters/rdbms-exporter/` | RDBMS adapter, uses `ExporterFactory` SPI |
| `RecordingExporter` | `zeebe/test-util/` | In-memory test exporter for engine tests |
| `DebugLogExporter` | `zeebe/broker/.../debug/` | Minimal: logs records as JSON, no position ack |
| `MetricsExporter` | `zeebe/broker/.../metrics/` | Demonstrates filter + `InstantSource` clock usage |

## Common Pitfalls

- Never hold references to `Record` objects across `export()` calls — the underlying buffer is reused. Call `record.toJson()` or `record.clone()` to retain.
- Never add a method to `Exporter` or `RecordFilter` without a default implementation — this breaks third-party exporters.
- Never add runtime dependencies beyond `zeebe-protocol`, `slf4j-api`, and `micrometer-core` to this module's POM.
- Always use `Context.clock()` instead of `System.currentTimeMillis()` when comparing against record timestamps.
- The `configure()` instance used at startup validation is discarded — do not allocate resources there; defer to `open()`.

## Testing

- Tests use JUnit 4 (legacy), AssertJ. See `ExporterTest.java`.
- Build/test scoped: `./mvnw -pl zeebe/exporter-api -am test -DskipITs -DskipChecks -T1C`
- License header: Apache 2.0 (set via `license.header.file` property in POM).

## Essential Files

- `src/main/java/.../Exporter.java` — main SPI interface
- `src/main/java/.../context/Context.java` — configuration context with `RecordFilter`
- `src/main/java/.../context/Controller.java` — runtime control (position ack, metadata, scheduling)
- `src/main/java/.../context/Configuration.java` — typed config instantiation
- `revapi.json` — backward compatibility rules for API evolution
- `pom.xml` — minimal dependencies, Revapi plugin configuration