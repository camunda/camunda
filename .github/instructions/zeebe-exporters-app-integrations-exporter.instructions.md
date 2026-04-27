```yaml
---
applyTo: "zeebe/exporters/app-integrations-exporter/**"
---
```
# App Integrations Exporter

## Purpose

This module is a Zeebe exporter that forwards workflow engine records as JSON events over HTTP to an external App Integration Backend (e.g., Slack, Teams, webhooks). It implements the `io.camunda.zeebe.exporter.api.Exporter` SPI, batches records, and delivers them via HTTP POST with retry and backpressure semantics.

## Architecture

The module has five layers, each in its own package under `io.camunda.exporter.appint`:

1. **Exporter entry point** (`AppIntegrationsExporter`) — implements `Exporter` SPI; delegates to `Subscription`
2. **Config** (`config/`) — `Config` POJO hydrated by Zeebe's `context.getConfiguration().instantiate()`, validated by `ConfigValidator`, split into `BatchConfig` record for batch-specific settings
3. **Mapper** (`mapper/`) — `RecordMapper<T>` interface with `SupportedRecordsMapper` implementation that filters and converts `Record<?>` to `Event` sealed types
4. **Subscription** (`subscription/`) — `Subscription<T>` orchestrates batching (`Batch<T>`), flushing, and async dispatch; `SubscriptionFactory` wires all components together
5. **Transport** (`transport/`) — `Transport<T>` interface with `HttpTransportImpl` using Apache HttpClient + Failsafe retry/timeout; `Authentication` sealed interface for auth strategies

### Data Flow

```
Record<?> → AppIntegrationsExporter.export()
         → Subscription.exportRecord()
         → RecordMapper.supports() → filter unsupported records (update position directly)
         → RecordMapper.map() → Event sealed type
         → Batch.addRecord() → accumulate until full or time threshold
         → Subscription.flush() → Dispatcher.dispatch(Runnable)
         → Transport.send(List<Event>) → HTTP POST as BatchRequest JSON
         → Controller.updateLastExportedRecordPosition()
```

## Key Abstractions

- **`Exporter`** (from `zeebe-exporter-api`): SPI contract with `configure()`, `open()`, `export()`, `close()` lifecycle. Never instantiate dependencies in `configure()`; defer to `open()`.
- **`Subscription<T>`**: Thread-safe batch orchestrator. Uses `ReentrantLock` for concurrent access between the exporter thread (calls `exportRecord`) and the scheduled flush task (calls `attemptFlush`). The `closed` field is `volatile`.
- **`Batch<T>`**: Accumulates mapped entries up to `batchSize` or `batchIntervalMs`. Tracks `lastLogPosition` for position updates. Rejects records with positions ≤ current (monotonic ordering). Returns a defensive copy from `getEntries()`.
- **`Dispatcher`** / `DispatcherImpl`: Single-threaded `ExecutorService` with `Semaphore` limiting in-flight batches (`maxBatchesInFlight`). Jobs that fail are retried indefinitely within the executor until success.
- **`Transport<T>`** / `HttpTransportImpl`: Sends `BatchRequest` JSON via HTTP POST. Uses Failsafe `RetryPolicy` (retries on `TransportException` only) composed with `Timeout`. Distinguishes `TransportClientException` (4xx, no retry) from `TransportException` (5xx/IO, retried).
- **`Event`**: Sealed interface with `UserTaskEvent` as the only current variant. Uses `@JsonUnwrapped` on nested metadata records (`EventMetaData`, `ProcessMetaData`, `UserTaskMetaData`) to flatten JSON output.
- **`Authentication`**: Sealed interface — `ApiKey` (sends `X-API-KEY` header) or `None` (no auth). Extend this when adding new auth mechanisms.
- **`RecordMapper<T>`**: Strategy interface with `supports()` filter and `map()` conversion. `SupportedRecordsMapper` currently handles `UserTaskIntent.CREATED`, `ASSIGNED`, `COMPLETED`, `CANCELED`.

## Extension Points

### Adding a new event type
1. Add a new record variant to the `Event` sealed interface in `event/Event.java` (e.g., `record IncidentEvent(...) implements Event {}`)
2. Add the corresponding `Intent` values to `SupportedRecordsMapper.intents` set
3. Add a `case` branch in `SupportedRecordsMapper.map()` with a private mapping method
4. The transport and batch layers are generic over `T` — no changes needed there

### Adding a new authentication method
1. Add a new case to the `Authentication` sealed interface in `transport/Authentication.java`
2. Handle the new case in `HttpTransportImpl.sendPostRequest()` switch expression
3. Wire it in `SubscriptionFactory.createDefault()` based on `Config` fields

### Adding new configuration properties
1. Add the field with a default value to `Config.java` (fluent setter pattern: `return this`)
2. Add validation rules in `ConfigValidator.validate()` if the field is required
3. Wire the field through `SubscriptionFactory.createDefault()` into the appropriate config record

## Invariants

- `Subscription.exportRecord()` and `attemptFlush()` are mutually exclusive via `ReentrantLock` — never bypass the lock
- `Batch` enforces monotonically increasing positions — records with position ≤ `lastLogPosition` are silently dropped
- `Batch.addRecord()` throws `IllegalStateException` if the batch is already full — always check `shouldFlush()` before adding
- Position is updated only after successful transport delivery (or after `continueOnError` skips the failure)
- For unsupported records, position is updated immediately only when no batch is in-flight (`hasNoActiveBatch()`)
- `DispatcherImpl` retries failed jobs forever within its single thread — transport-level retry (Failsafe) handles transient HTTP errors, while dispatcher-level retry handles all other exceptions
- `TransportClientException` (4xx) is NOT retried by Failsafe — only `TransportException` triggers retries

## Common Pitfalls

- Do not call `controller.updateLastExportedRecordPosition()` before the batch is fully delivered — this would cause data loss on restart
- Do not remove the `@JsonUnwrapped` annotations from `Event` records — the external backend expects a flat JSON structure
- Do not add mutable state to `Event` records — they are shared across threads via the dispatcher
- The `Config` class uses JavaBean-style getters/setters (not a record) because Zeebe's `instantiate()` requires a no-arg constructor and setters
- `HttpTransportImpl` uses Apache HttpClient 4.x (`org.apache.http`), not the 5.x API — import paths differ

## Testing

- **Unit tests**: `BatchTest`, `SubscriptionTest`, `DispatcherTest`, `ConfigTest` — mock `Transport` and `RecordMapper`, use Awaitility for async assertions
- **Integration tests**: `AppIntegrationsExporterBatchIT` — uses WireMock for HTTP stubbing and `ExporterTestController`/`ExporterTestContext` from `zeebe-exporter-test` to simulate the Zeebe exporter lifecycle
- **Build**: `./mvnw install -pl zeebe/exporters/app-integrations-exporter -am -Dquickly`
- **Unit tests only**: `./mvnw verify -pl zeebe/exporters/app-integrations-exporter -DskipITs`
- **Integration tests only**: `./mvnw verify -pl zeebe/exporters/app-integrations-exporter -DskipUTs`
- Use `ProtocolFactory` from `zeebe-protocol-test-util` to generate test records with `factory.generateRecord(ValueType.USER_TASK, ...)`
- Always use `Awaitility.await()` for async dispatch assertions — never `Thread.sleep` (except `SubscriptionTest.shouldFlushBatchOnAttemptFlushWhenTimeThresholdReached` which waits for batch interval)

## Key Files

| File | Role |
|------|------|
| `src/main/java/.../AppIntegrationsExporter.java` | Exporter SPI entry point; lifecycle and scheduled flush |
| `src/main/java/.../subscription/Subscription.java` | Core orchestrator: batching, locking, dispatch, position tracking |
| `src/main/java/.../subscription/SubscriptionFactory.java` | Wires all components; creates `JsonMapper`, `HttpTransportImpl`, `SupportedRecordsMapper` |
| `src/main/java/.../mapper/SupportedRecordsMapper.java` | Record filtering and mapping to `Event` sealed types |
| `src/main/java/.../transport/HttpTransportImpl.java` | HTTP POST with Failsafe retry/timeout, auth header injection |
| `src/main/java/.../event/Event.java` | Sealed event model with `@JsonUnwrapped` flat JSON structure |
| `src/test/java/.../AppIntegrationsExporterBatchIT.java` | End-to-end integration test with WireMock |