```yaml
---
applyTo: "zeebe/util/src/**"
---
```
# zeebe/util — Shared Utility Library for Zeebe

Low-level utility module (`io.camunda.zeebe.util`) providing foundational abstractions used across the entire Zeebe engine, broker, gateway, and exporter stack. This is a leaf dependency — nearly every Zeebe module depends on it. Treat changes here as high-impact; they ripple across the codebase.

## Architecture

The module is organized into focused sub-packages under `io.camunda.zeebe.util`:

- **Root package**: Core utilities — `Either`, `EnsureUtil`, `FileUtil`, `VersionUtil`, `SemanticVersion`, `ExponentialBackoff`, `FeatureFlags`, `Environment`, `ByteValue`, `CloseableSilently`, `AtomicUtil`, `StringUtil`, `StreamUtil`, `HashUtil`, `LockUtil`, `ReflectUtil`, `DateUtil`, `SbeUtil`, `VarDataUtil`, `PartitionUtil`, `Loggers`, `ObjectSizeEstimator`
- **`buffer/`**: Agrona `DirectBuffer` read/write abstractions (`BufferReader`, `BufferWriter`, `BufferUtil`, `DirectBufferWriter`)
- **`allocation/`**: Off-heap memory management (`BufferAllocator`, `AllocatedBuffer`, `AllocatedDirectBuffer`)
- **`collection/`**: Multi-dimensional data structures (`Table`, `Map3D`, `MArray`, `Tuple`, `ReusableObjectList`)
- **`concurrency/`**: `CompletableFuture` composition helpers (`FuturesUtil`)
- **`health/`**: Component health monitoring framework (`HealthMonitor`, `HealthMonitorable`, `HealthReport`, `HealthStatus`, `FailureListener`, `DelayedHealthIndicator`, `MemoryHealthIndicator`)
- **`micrometer/`**: Micrometer metrics extensions (`StatefulGauge`, `StatefulMeterRegistry`, `BoundedMeterCache`, `EnumMeter`, `MicrometerUtil`, `ExtendedMeterDocumentation`)
- **`logging/`**: `ThrottledLogger` for rate-limited log output
- **`error/`**: Fatal error handling (`FatalErrorHandler`, `VirtualMachineErrorHandler`)
- **`exception/`**: Exception taxonomy (`RecoverableException`, `UnrecoverableException`)
- **`retry/`**: Resilience4j-based retry (`RetryDecorator`, `RetryConfiguration`)
- **`jar/`**: External JAR classloading for exporter plugins (`ExternalJarClassLoader`, `ExternalJarRepository`)
- **`migration/`**: Version compatibility checks (`VersionCompatibilityCheck`)
- **`modelreader/`**: BPMN model introspection (`ProcessModelReader`)
- **`cache/`**: Caffeine cache metrics bridge (`CaffeineCacheStatsCounter`)
- **`liveness/`**: Spring Boot liveness auto-configuration for memory health

## Key Abstractions

### Either Monad (`Either.java`)
Sealed interface with `Right<L,R>` (success) and `Left<L,R>` (error) record implementations. Provides `map`, `mapLeft`, `flatMap`, `fold`, `thenDo`, `ifRightOrLeft`, and stream `collector()`/`collectorFoldingLeft()`. Used pervasively in REST controllers, request mappers, and engine processors for railway-oriented error handling. Always use `fold()` to convert to a single type; use `flatMap()` to chain operations that may fail.

### Health Monitoring (`health/`)
Tree-structured health model: `HealthMonitorable` components report `HealthReport` records containing `HealthStatus` (HEALTHY → UNHEALTHY → DEAD, ordered by severity). `HealthMonitor` aggregates children using worst-status propagation. `FailureListener` provides callbacks for status transitions. `DelayedHealthIndicator` wraps Spring Boot health checks with time-tolerance to hide transient outages.

### Micrometer Extensions (`micrometer/`)
`StatefulGauge` solves Micrometer's gauge-uniqueness limitation by pairing a `Gauge` with mutable `GaugeState` (backed by `AtomicLong` storing raw double bits). `StatefulMeterRegistry` extends `CompositeMeterRegistry` to track and deduplicate stateful gauges across re-registrations. `BoundedMeterCache` uses Caffeine to bound high-cardinality tag metrics (default 500 entries), auto-removing evicted meters from the registry. `EnumMeter` creates one gauge per enum value, ensuring exactly one reads 1 at a time.

### Buffer I/O (`buffer/`)
`BufferWriter` and `BufferReader` are the fundamental serialization interfaces for Zeebe's zero-copy protocol layer (used by SBE codecs, `DbKey`/`DbValue` in zb-db, record values). `BufferWriter.getLength()` returns the byte count before writing; `BufferReader.wrap()` creates a view on existing buffer data. `BufferUtil` provides conversion between `DirectBuffer`, `String`, `byte[]`, and hex dump formatting.

### Version Management
`SemanticVersion` is a record implementing SemVer 2.0.0 with `Comparable`, caching parsed results in a `ConcurrentHashMap`. `VersionUtil` resolves the runtime version from env vars (`ZEEBE_VERSION_OVERRIDE`), properties files, or JAR manifest. `VersionCompatibilityCheck` uses sealed interface hierarchies (`Compatible`, `Incompatible`, `Indeterminate`) for exhaustive version migration validation.

## Design Patterns

- **Sealed interfaces + records**: `Either`, `VersionCompatibilityCheck.CheckResult`, `HealthIssue` use sealed hierarchies for exhaustive pattern matching. Extend this pattern when adding new result types.
- **`CloseableSilently`**: `AutoCloseable` variant that suppresses checked exceptions in `close()`. Use for resources that cannot meaningfully fail on close (buffers, timers, metric wrappers).
- **Functional interfaces**: `CheckedRunnable`, `TriFunction`, `RetryDelayStrategy` — keep as single-method interfaces for lambda compatibility.
- **`@VisibleForTesting`**: Custom annotation (not Guava's) marking package-private/wider-than-necessary visibility explicitly for test access. Use it when exposing internals for testability.
- **Thread-local Kryo**: `ObjectSizeEstimator` uses `ThreadLocal<Kryo>` for thread-safe, allocation-free size estimation.

## Extension Points

- **New utility class**: Add to the root package `io.camunda.zeebe.util`. Follow the `*Util` suffix convention for static-only classes with private constructors.
- **New feature flag**: Follow the inline instructions in `FeatureFlags.java` — add field, default constant, constructor parameter, getter/setter, and update `FeatureFlagsCfg` and broker YAML templates.
- **New health component**: Implement `HealthMonitorable`, return `HealthReport` from `getHealthReport()`, register with `HealthMonitor.registerComponent()`.
- **New meter documentation**: Implement `ExtendedMeterDocumentation`, override `getTimerSLOs()` or `getDistributionSLOs()` for custom histogram buckets.
- **New collection type**: Add to `collection/` package following `Table`/`Map3D` patterns — interface with `simple()`, `concurrent()`, and `ofEnum()` factory methods.

## Invariants

- Never modify `Either` semantics: Right = success, Left = error. All consumers rely on this convention.
- `HealthStatus` ordinal ordering (HEALTHY < UNHEALTHY < DEAD) is load-bearing — `COMPARATOR` and `combine()` depend on it. Never reorder enum constants.
- `BufferWriter.getLength()` must return the exact byte count that `write()` will produce. Mismatch causes buffer overflows/underflows in SBE encoding.
- `SemanticVersion.compareTo()` deliberately ignores `buildMetadata` per SemVer spec — this means `compareTo` is not consistent with `equals`.
- `StatefulMeterRegistry` uses `get()` + `putIfAbsent()` instead of `computeIfAbsent()` to avoid ConcurrentHashMap deadlocks (see issue #33941).

## Common Pitfalls

- Do not add heavyweight dependencies to this module — it is a transitive dependency of nearly everything. Check `pom.xml` carefully.
- `ExponentialBackoff` operates in milliseconds, not `Duration`. Convert appropriately when integrating.
- `ThrottledLogger` is not thread-safe (uses plain `lastLogTime` field). Acceptable for single-actor usage but do not share across threads without synchronization.
- `FeatureFlags` uses constructor parameter ordering — be careful when adding new flags to maintain correct positional mapping.
- `BufferUtil.cloneBuffer()` only supports `UnsafeBuffer` and `ExpandableArrayBuffer` — throws `RuntimeException` for other `DirectBuffer` implementations.

## Key Files

- `main/.../Either.java` — Sealed Either monad, used across all API layers
- `main/.../health/HealthMonitor.java` + `HealthReport.java` — Component health tree model
- `main/.../micrometer/StatefulGauge.java` + `StatefulMeterRegistry.java` — Stateful gauge deduplication
- `main/.../buffer/BufferWriter.java` + `BufferReader.java` — Zero-copy serialization interfaces
- `main/.../FeatureFlags.java` — Runtime feature toggle registry with inline extension guide

## Build

```
./mvnw -pl zeebe/util -am test -DskipITs -DskipChecks -T1C
```