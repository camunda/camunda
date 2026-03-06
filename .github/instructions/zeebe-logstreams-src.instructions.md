```yaml
---
applyTo: "zeebe/logstreams/src/**"
---
```
# Zeebe Logstreams Module

## Purpose

Append-only log abstraction with built-in flow control and backpressure for the Zeebe process engine. This module provides the ordered, position-indexed event stream that underpins all event sourcing in the broker. It sits between the physical storage layer (`LogStorage`, implemented by the journal module) and the stream processing layer (`stream-platform`), handling serialization, sequencing, position assignment, and adaptive rate limiting.

## Architecture

### Layers

1. **Public API** (`log/`): Interfaces consumed by the stream processor and broker — `LogStream`, `LogStreamWriter`, `LogStreamReader`, `LogStreamBatchReader`, `LogAppendEntry`, `LoggedEvent`, `WriteContext`.
2. **Storage SPI** (`storage/`): `LogStorage` and `LogStorageReader` — the pluggable storage backend contract. Implemented externally by the journal module.
3. **Implementation** (`impl/log/`): `LogStreamImpl`, `Sequencer`, `LogStreamReaderImpl`, `LogStreamBatchReaderImpl`, serialization, and the binary `LogEntryDescriptor` wire format.
4. **Serialization** (`impl/serializer/`): `LogAppendEntrySerializer`, `SequencedBatchSerializer`, `DataFrameDescriptor` — converts `LogAppendEntry` lists into framed byte buffers for storage.
5. **Flow control** (`impl/flowcontrol/`): `FlowControl`, `RequestLimiter`, `StabilizingAIMDLimit`, `RateLimitThrottle`, `RingBuffer` — adaptive backpressure via concurrency and rate limiting.
6. **Metrics** (`impl/`): `LogStreamMetrics`, `LogStreamMetricsDoc`, `SequencerMetrics` — Micrometer-based observability for appends, commits, flow control outcomes, and latency.

### Data Flow

```
LogAppendEntry → Sequencer.tryWrite()
  → FlowControl.tryAcquire() [backpressure check]
  → position assignment (monotonic, under ReentrantLock)
  → SequencedBatch → SequencedBatchSerializer → LogStorage.append()
  → LogStorage.AppendListener callbacks: onWrite() → onCommit()
  → LogStorage.CommitListener.onCommit() → LogRecordAwaiter notification
  → LogStreamReader reads back LoggedEvent from LogStorageReader
```

### Key Abstractions

- **`LogStream`** (`log/LogStream.java`): Top-level facade. Creates readers/writers, owns flow control, manages commit listeners. Use `LogStream.builder()` to construct.
- **`LogStreamWriter`** (`log/LogStreamWriter.java`): Functional interface for atomic batch writes. Returns `Either<WriteFailure, Long>`. The sole implementation is `Sequencer`.
- **`Sequencer`** (`impl/log/Sequencer.java`): Serializes concurrent writes under a `ReentrantLock`, assigns monotonically increasing positions, delegates to `LogStorage.append()`. The central write path.
- **`LogAppendEntry`** (`log/LogAppendEntry.java`): Immutable record to be appended — carries `key`, `sourceIndex`, `RecordMetadata`, and `UnifiedRecordValue`. Factory methods `LogAppendEntry.of()` and `LogAppendEntry.ofProcessed()`.
- **`LoggedEvent`** (`log/LoggedEvent.java`): A read-back event from the log. Provides position, key, timestamp, metadata/value buffers. Implementation is `LoggedEventImpl` backed by Agrona `DirectBuffer`.
- **`WriteContext`** (`log/WriteContext.java`): Sealed interface distinguishing write origins — `UserCommand`, `ProcessingResult`, `InterPartition`, `Scheduled`, `Internal`. Controls flow control behavior (e.g., `Internal` always bypasses limits).
- **`LogStorage`** / **`LogStorageReader`** (`storage/`): SPI for the physical storage backend. `LogStorage.append()` takes position range + `BufferWriter` + `AppendListener`. The reader is a sequential `Iterator<DirectBuffer>` over blocks.
- **`FlowControl`** (`impl/flowcontrol/FlowControl.java`): Implements `LogStorage.AppendListener`. Maintains in-flight entries in a `RingBuffer`, applies request concurrency limiting (AIMD) and write rate limiting (Guava `RateLimiter`). Thread-safe across sequencer, raft, and stream processor threads.
- **`LogEntryDescriptor`** (`impl/log/LogEntryDescriptor.java`): Binary wire format — VERSION (2B), FLAGS (1B), reserved (1B), POSITION (8B), SOURCE_EVENT_POSITION (8B), KEY (8B), TIMESTAMP (8B), METADATA_LENGTH (4B), metadata bytes, value bytes. All fields use `Protocol.ENDIANNESS`.
- **`DataFrameDescriptor`** (`impl/serializer/DataFrameDescriptor.java`): Legacy 12-byte dispatcher framing header. Kept for backwards compatibility; wraps each log entry.

## Relationships to Other Modules

- **`zeebe/journal`**: Implements `LogStorage` / `LogStorageReader` — the actual file-based segmented journal.
- **`zeebe/stream-platform`**: Primary consumer — `StreamProcessor` uses `LogStreamWriter` to write processing results, `LogStreamReader` to replay, and `FlowControl.onProcessed()` to complete the backpressure lifecycle.
- **`zeebe/broker`**: Constructs `LogStream` instances per partition, wires flow control limits from configuration, calls `FlowControl.onExported()` from exporter position updates.
- **`zeebe/protocol` / `zeebe/protocol-impl`**: Provides `RecordMetadata`, `UnifiedRecordValue`, `Intent`, `ValueType`, `RecordType` used in entries and flow control whitelisting.
- **`zeebe/scheduler`**: `ActorClock` is used for timestamps in `RateMeasurement`.

## Extension Points

- **New write context types**: Add a new record to the `WriteContext` sealed interface in `log/WriteContext.java`. Update the `switch` in `FlowControl.tryAcquireInternal()` and `LogStreamMetrics.tagForContext()`.
- **New whitelisted commands**: Add intents to `WhiteListedCommands.WHITE_LISTED_COMMANDS` set in `impl/flowcontrol/WhiteListedCommands.java`.
- **New metrics**: Add enum constants to `LogStreamMetricsDoc`, register in `LogStreamMetrics` constructor, expose via the appropriate flow control or sequencer path.
- **Custom flow control limits**: Implement `com.netflix.concurrency.limits.Limit` and pass via `LogStreamBuilder.withRequestLimit()`. The default is `StabilizingAIMDLimit`.

## Invariants

- Positions are monotonically increasing starting at 1. Each entry in a batch gets `firstPosition + index`. Never assign position 0 or negative.
- Batch writes are atomic — all entries succeed or none. The `Sequencer` holds a `ReentrantLock` during position assignment and `LogStorage.append()`.
- `FlowControl.registerEntry()` must be called inside the sequencer lock, **before** `logStorage.append()`, so callbacks (`onWrite`/`onCommit`) always find the entry in the `RingBuffer`.
- `LogEntryDescriptor.VERSION` is currently `1`. If incremented, update `getMetadataLength()` which has version-dependent parsing for backwards compatibility.
- `DataFrameDescriptor.HEADER_LENGTH` (12 bytes) and `FRAME_ALIGNMENT` (8 bytes) are legacy framing — do not change without coordinating with all `LoggedEvent` readers.
- The `RingBuffer` capacity must be a power of two (enforced by `BitUtil.findNextPositivePowerOfTwo`). Entries are indexed by `position & mask`.
- `LogAppendEntryMetadata.copyMetadata()` snapshots mutable `RecordMetadata` fields into short arrays — required because metadata objects are reused between writes.

## Common Pitfalls

- **Forgetting `WriteContext`**: Every `tryWrite` call requires a `WriteContext`. Using the wrong context (e.g., `UserCommand` instead of `Internal`) changes backpressure behavior.
- **Mutable metadata**: `RecordMetadata` objects are reused. The `LogAppendEntryMetadata.copyMetadata()` call in `FlowControl` exists because the original metadata may be overwritten before async callbacks fire.
- **Thread safety in FlowControl**: `tryAcquire` runs concurrently outside the sequencer lock; `registerEntry` runs inside it; `onWrite`/`onCommit` run on the raft thread; `onProcessed` runs on the stream processor thread. All synchronization relies on the `AtomicReferenceArray` in `RingBuffer`.
- **Fragment size limits**: `Sequencer.canWriteEvents()` accounts for frame headers and alignment per entry. Exceeding `maxFragmentSize` silently rejects writes.
- **LogStreamReader is not thread-safe**: Annotated `@NotThreadSafe`. Each consumer must create its own reader via `LogStream.newLogStreamReader()`.

## Testing

- Run scoped: `./mvnw -pl zeebe/logstreams -am test -DskipITs -DskipChecks -T1C`
- Test utilities published as a test-jar: `LogStreamRule`, `TestLogStream`, `TestEntry`, `ListLogStorage`.
- `ListLogStorage` is an in-memory `LogStorage` implementation for unit tests.
- Use `TestLogStreamBuilder` to construct test `LogStream` instances with `ListLogStorage`.

## Key Files

| File | Role |
|------|------|
| `log/LogStream.java` | Public API facade — builder, reader/writer factory, flow control access |
| `impl/log/Sequencer.java` | Core write path — position assignment, serialization, storage append |
| `impl/flowcontrol/FlowControl.java` | Backpressure — request limiting, rate limiting, in-flight tracking |
| `impl/log/LogEntryDescriptor.java` | Binary wire format — field offsets, getters/setters for the log entry header |
| `impl/serializer/LogAppendEntrySerializer.java` | Entry serialization — writes framed log entries into buffers |
| `impl/flowcontrol/RingBuffer.java` | Lock-free position-indexed buffer for tracking in-flight entries |