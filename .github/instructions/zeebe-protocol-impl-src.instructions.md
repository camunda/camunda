```yaml
---
applyTo: "zeebe/protocol-impl/src/**"
---
```
# zeebe/protocol-impl — Protocol Implementation Module

## Purpose

This module provides the concrete implementations of the Zeebe wire protocol defined in `zeebe/protocol`. It contains MsgPack-based record value classes for all engine record types, SBE-encoded request/response message types for gateway-broker communication, and utilities for serialization between MsgPack, JSON, and SBE formats. Every record written to the Zeebe log and every message exchanged between gateway and broker flows through this module.

## Architecture

The module has four layers:

1. **`record/`** — MsgPack-serialized record values (`UnifiedRecordValue` subclasses) that represent all engine record types (jobs, process instances, deployments, etc.)
2. **`encoding/`** — SBE-encoded request/response messages for gateway-broker transport (`ExecuteCommandRequest`, `ErrorResponse`, `BrokerInfo`, etc.)
3. **`stream/job/`** — Interfaces and implementations for job activation streaming (`ActivatedJob`, `JobActivationProperties`)
4. **Root utilities** — `PartitionUtil` (deterministic key-to-partition hashing), `SubscriptionUtil` (correlation key routing), `Loggers`

### Data Flow

- **Inbound commands**: gRPC/REST → gateway creates `ExecuteCommandRequest` (SBE) → broker deserializes → engine creates domain-specific `*Record` (MsgPack) → written to log
- **Log records**: `RecordMetadata` (SBE header) + `UnifiedRecordValue` subclass (MsgPack body) stored together in the append-only log
- **Outbound/export**: `CopiedRecord` wraps a `UnifiedRecordValue` with metadata for exporters; `MsgPackConverter` serializes to JSON for debugging/export

## Key Abstractions

### UnifiedRecordValue (`record/UnifiedRecordValue.java`)
Base class for ALL engine record types. Extends `UnpackedObject` (MsgPack serializable) and implements `RecordValue`. Contains the canonical `fromValueType(ValueType)` factory switch that maps every `ValueType` enum to its concrete record class. The inner `ClassToValueType` map provides reverse lookup. When adding a new record type, add a case here.

### RecordMetadata (`record/RecordMetadata.java`)
SBE-encoded metadata attached to every log record. Contains `RecordType`, `ValueType`, `Intent`, `rejectionType/Reason`, `authorization` (`AuthInfo`), `agent` (`AgentInfo`), `brokerVersion`, `recordVersion`, `operationReference`, and `batchOperationReference`. Implements `BufferWriter`/`BufferReader` for zero-copy SBE serialization.

### CopiedRecord (`record/CopiedRecord.java`)
Immutable snapshot of a record + metadata, used by exporters. Supports deep `copyOf()` via reflective instantiation. Thread-safety warning: `toJson()` is NOT thread-safe due to `ArrayProperty` iteration side effects.

### SbeBufferWriterReader (`encoding/SbeBufferWriterReader.java`)
Abstract base for SBE-encoded messages. Provides `wrap()`/`write()`/`tryWrap()` with SBE header handling. Subclasses provide encoder/decoder via `getBodyEncoder()`/`getBodyDecoder()`.

### MsgPackConverter (`encoding/MsgPackConverter.java`)
Static utility for bidirectional MsgPack ↔ JSON conversion and MsgPack ↔ Map deserialization. Uses Jackson with `MessagePackFactory` and `MappingJsonFactory`. Thread-safe factories, but `convertJsonSerializableObjectToJson()` may not be thread-safe depending on the object.

### AuthInfo (`encoding/AuthInfo.java`)
MsgPack-serialized authentication data carrying format (`JWT`, `PRE_AUTHORIZED`, `UNKNOWN`), raw auth data, and decoded claims. Propagated through `RecordMetadata` and `CommandDistributionRecord`.

## Adding a New Record Type

1. Create `record/value/<domain>/<Name>Record.java` extending `UnifiedRecordValue`
2. Implement the corresponding `*RecordValue` interface from `zeebe/protocol`
3. Declare properties in constructor using MsgPack property types (`StringProperty`, `LongProperty`, `EnumProperty`, `DocumentProperty`, `ArrayProperty`, `ObjectProperty`, etc.)
4. Pass the correct `expectedDeclaredProperties` count to `super(N)` — must match the number of `declareProperty()` calls
5. Add a case to `UnifiedRecordValue.fromValueType()` mapping `ValueType.<NEW_TYPE>` → `new <Name>Record()`
6. Create a golden file at `src/test/resources/protocol/records/golden/<Name>Record.golden` — copy the source file as the golden snapshot
7. If the record is distributable, register it in `CommandDistributionRecord.RECORDS_BY_TYPE`
8. Every new property **must** have a default value for backward compatibility (enforced by `RecordGoldenFilesTest`)

## Record Value Implementation Pattern

Follow `JobRecord` as the canonical example:

```java
public final class JobRecord extends UnifiedRecordValue implements JobRecordValue {
  private final StringProperty typeProp = new StringProperty(TYPE_KEY, EMPTY_STRING);
  private final LongProperty deadlineProp = new LongProperty(DEADLINE_KEY, -1);
  // ...
  public JobRecord() {
    super(24); // must match declareProperty count
    declareProperty(deadlineProp).declareProperty(typeProp)...;
  }
}
```

- Use `static final StringValue` keys to avoid repeated allocations
- Provide `@JsonIgnore` on `DirectBuffer`-returning getter variants (e.g., `getTypeBuffer()`)
- Public getters implement the `*RecordValue` interface returning Java types (`String`, `Map`, `Set`)
- Fluent setters return `this` for chaining
- Provide `wrap(SameRecord other)` for deep-copy from another instance

## Encoding Message Pattern

Follow `ExecuteCommandRequest` / `ErrorResponse` as examples:

- Implement `BufferWriter` + `BufferReader` directly (not `SbeBufferWriterReader` — that's for simpler messages)
- Constructor calls `reset()` to initialize null values from SBE encoder constants
- `wrap()`: decode SBE header → fixed-length fields → variable-length fields in schema order; assert `bodyDecoder.limit() == frameEnd`
- `write()`: encode via `wrapAndApplyHeader()` → fixed fields → variable-length `put*()` calls
- `getLength()`: sum header + block + all variable-length header/data sizes

## Invariants

- **Property count accuracy**: `super(N)` in `UnifiedRecordValue` subclass constructors must exactly match the number of `declareProperty()` calls — incorrect counts cause ArrayIndexOutOfBoundsException or wasted memory
- **Golden file backward compatibility**: Never remove or change the type of a released property. New properties must have defaults. `RecordGoldenFilesTest` enforces this
- **SBE field ordering**: Variable-length fields in `wrap()` and `write()` must be read/written in the exact order defined in the SBE schema — out-of-order access corrupts the buffer
- **fromValueType completeness**: Every non-null `ValueType` must have a mapping in `UnifiedRecordValue.fromValueType()` — enforced by `UnifiedRecordValueTest`
- **Naming convention**: Record class names must be `${CamelCase(ValueType)}Record` — enforced by `UnifiedRecordValueTest`

## Common Pitfalls

- Forgetting to add a new `ValueType` case in `UnifiedRecordValue.fromValueType()` causes NPE in the engine
- Forgetting to create/update the golden file causes `RecordGoldenFilesTest` to fail
- Adding a property without a default breaks backward compatibility during rolling upgrades
- Using `CopiedRecord.toJson()` from multiple threads concurrently causes corruption (due to `ArrayProperty` iteration)
- Forgetting to register distributable records in `CommandDistributionRecord.RECORDS_BY_TYPE` causes `IllegalStateException` during command distribution

## Testing

- `UnifiedRecordValueTest` — verifies every `ValueType` has a non-null mapping and correct naming
- `RecordGoldenFilesTest` — verifies record properties match golden file snapshots for backward compatibility; new properties must have defaults
- `JsonSerializableToJsonTest` — verifies JSON serialization round-trip for all record types
- Run scoped: `./mvnw -pl zeebe/protocol-impl -am test -DskipITs -DskipChecks -T1C`

## Key Dependencies

- `zeebe/protocol` — SBE-generated codecs (`MessageHeaderEncoder/Decoder`, `RecordMetadataEncoder/Decoder`, `ValueType`, `Intent`, `RecordType`) and `RecordValue` interfaces
- `zeebe/msgpack-value` — `UnpackedObject` base class and property types (`StringProperty`, `LongProperty`, `DocumentProperty`, etc.)
- `zeebe/util` — `BufferWriter`/`BufferReader` interfaces, `BufferUtil`, `VersionUtil`
- `jackson-dataformat-msgpack` — MsgPack ↔ JSON conversion in `MsgPackConverter`
- `agrona` — `DirectBuffer`/`MutableDirectBuffer`/`UnsafeBuffer` for zero-copy I/O

## Essential Files

- `record/UnifiedRecordValue.java` — base class + `ValueType` → record factory
- `record/RecordMetadata.java` — SBE record metadata (header of every log entry)
- `record/CopiedRecord.java` — immutable record snapshot for exporters
- `encoding/MsgPackConverter.java` — MsgPack/JSON serialization hub
- `encoding/SbeBufferWriterReader.java` — abstract SBE message base
- `record/value/job/JobRecord.java` — canonical record implementation example
- `encoding/ExecuteCommandRequest.java` — canonical SBE message example