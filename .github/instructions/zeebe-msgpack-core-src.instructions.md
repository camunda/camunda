```yaml
---
applyTo: "zeebe/msgpack-core/src/**"
---
```
# zeebe/msgpack-core — MsgPack Spec Module

## Purpose

Zero-dependency (except Agrona) implementation of the [MessagePack](https://msgpack.org/) binary serialization spec. Provides low-level reader/writer for encoding and decoding MsgPack values on Agrona `DirectBuffer`/`MutableDirectBuffer`. This is the foundational serialization layer used by the higher-level `zeebe/msgpack-value` typed-object system, the engine's variable/document storage, FEEL expression evaluation, protocol records, and the gateway layers.

## Architecture

All classes reside in `io.camunda.zeebe.msgpack.spec`. The module has no subpackages and no Spring/DI involvement — it is a pure library.

### Core Data Flow

```
Raw bytes (DirectBuffer) ──► MsgPackReader ──► typed Java values (long, double, boolean, DirectBuffer slices)
                                              ──► MsgPackToken (generic token with type tag)
typed Java values ──► MsgPackWriter ──► Raw bytes (MutableDirectBuffer)
```

### Class Roles

| Class | Role |
|-------|------|
| `MsgPackCodes` | Constants for all MsgPack format bytes (0xc0=NIL, 0xc2=FALSE, etc.) and bit-mask helper methods (`isFixInt`, `isFixedMap`, etc.). Wire byte order is `BIG_ENDIAN`. |
| `MsgPackFormat` | Enum mapping every format byte to a `MsgPackFormat` variant. Uses a 256-entry lookup table (`FORMAT_TABLE`) for O(1) byte→format resolution via `valueOf(byte)`. |
| `MsgPackType` | Enum of logical MsgPack types (INTEGER, FLOAT, BOOLEAN, STRING, BINARY, MAP, ARRAY, NIL, EXTENSION, NEVER_USED). Tracks `isScalar` — MAP and ARRAY are non-scalar. |
| `MsgPackReader` | Stateful reader wrapping a `DirectBuffer`. Provides typed read methods (`readInteger`, `readMapHeader`, `readStringLength`, etc.), a generic `readToken()`, and `skipValue()`/`skipValues(count)` for efficient traversal without deserialization. |
| `MsgPackWriter` | Stateful writer wrapping a `MutableDirectBuffer`. Provides typed write methods and static `getEncoded*Length` methods for pre-calculating buffer sizes. Supports `reserveMapHeader()`/`writeReservedMapHeader()` for maps with unknown size at write time. |
| `MsgPackToken` | Mutable value holder returned by `MsgPackReader.readToken()`. Carries type tag plus the decoded value (long, double, boolean, or buffer slice). Single instance is reused per reader — never store references across reads. |
| `MsgPackHelper` | Static constants for common encoded values: `EMTPY_OBJECT` (empty map), `EMPTY_ARRAY`, `NIL`. Also contains `ensurePositive()` guard. |
| `MsgpackException` | Base runtime exception for all MsgPack errors. |
| `MsgpackReaderException` | Thrown on read errors (unknown headers, negative sizes, NEVER_USED byte). |
| `MsgpackWriterException` | Thrown on write errors (negative header sizes). |

## Key Design Decisions

- **Agrona buffers only**: All I/O uses `DirectBuffer`/`MutableDirectBuffer` (never `byte[]` or `ByteBuffer` directly). This enables zero-copy integration with the engine's off-heap memory model.
- **Signed value semantics in writer**: `MsgPackWriter` treats integers as signed — `0xFFFFFFFF` is -1, not 2^32-1. This matches the engine's Java `long` usage.
- **Mutable token reuse**: `MsgPackReader` reuses a single `MsgPackToken` instance. Copy values before the next `readToken()` call.
- **No EXTENSION type support in readToken()**: `readToken()` throws on EXTENSION format. Only `skipValue()` handles EXTENSION correctly.
- **Size guards**: Both reader and writer reject negative sizes via `MsgPackHelper.ensurePositive()`, throwing the appropriate reader/writer exception subclass.

## Relationships to Other Modules

- **`zeebe/msgpack-value`** (direct consumer): Builds typed object model (`UnpackedObject`, `StringValue`, `LongValue`, `ArrayValue`, `DocumentValue`, etc.) on top of this reader/writer. Every `BaseValue.read()`/`write()` delegates here.
- **`zeebe/engine`**: Uses `MsgPackReader`/`MsgPackWriter` directly in `IndexedDocument`, `DocumentEntryIterator`, `DbVariableState`, `HeaderEncoder`, and various processor behaviors for variable/document manipulation.
- **`zeebe/protocol-impl`**: Record values (e.g., `JobRecord`, `UserTaskRecord`) serialize embedded documents via this module.
- **`zeebe/feel`** and **`zeebe/dmn`**: Transform between FEEL/DMN values and MsgPack using `MsgPackReader`/`MsgPackWriter`.
- **`zeebe/gateway-rest`**, **`zeebe/gateway-grpc`**, **`gateways/gateway-mapping-http`**, **`service/`**: Consume indirectly through `msgpack-value`.

## Extension Points

To add support for a new MsgPack format type:
1. Add the byte constant to `MsgPackCodes`.
2. Add a new `MsgPackFormat` enum variant mapping to the appropriate `MsgPackType`.
3. Handle the new format in `MsgPackFormat.toMessageFormat()` switch.
4. Add read logic in `MsgPackReader` (both typed method and `skipValue()`).
5. Add write logic in `MsgPackWriter` with a corresponding `getEncoded*Length` static method.

## Invariants

- All multi-byte values use `BIG_ENDIAN` byte order (`MsgPackCodes.BYTE_ORDER`).
- `MsgPackReader.wrap()` resets offset to 0 relative to the wrapped sub-buffer — `getOffset()` returns position within the wrapped slice, not the underlying buffer.
- `MsgPackWriter.wrap()` starts writing at the provided offset — `getOffset()` returns the absolute position in the underlying buffer.
- Header sizes must be non-negative — `ensurePositive()` is called before every header write and on 32-bit/64-bit unsigned reads.
- `MsgPackToken` is NOT safe to hold across multiple `readToken()` calls — values are overwritten.

## Common Pitfalls

- **Forgetting to call `wrap()` before read/write**: Reader/writer are stateful and start with empty/zero-capacity buffers.
- **Storing `MsgPackToken` references**: The reader reuses a single token instance. Extract primitive values or copy buffer slices immediately.
- **UINT64 overflow**: `readInteger()` returns `long` — unsigned 64-bit values exceeding `Long.MAX_VALUE` are rejected by `ensurePositive()`.
- **Offset semantics differ**: Reader offset is relative to the wrapped slice; writer offset is absolute.
- **skipValue for containers**: `skipValues()` recursively counts map entries (key+value pairs = size*2) and array entries, decrementing the count as it goes. Do not manually skip map/array contents after calling `skipValue()` on the container header.

## Testing Patterns

- Tests use a mix of JUnit 4 (`@RunWith(Parameterized.class)`) and JUnit 5 (`@ParameterizedTest`). The skipping test (`MsgPackSkippingTest`) is JUnit 5; all others are JUnit 4.
- `ByteArrayBuilder` is a test helper for fluent raw-byte construction — accepts `int...` args cast to bytes.
- `MsgPackUtil.encodeMsgPack()` uses the reference `org.msgpack:msgpack-core` library (test-scoped) to generate canonical MsgPack for comparison.
- Tests verify round-trip correctness: write with `MsgPackWriter` → read with `MsgPackReader` → assert values match.
- Tests verify boundary conditions: negative sizes, NEVER_USED byte (0xC1), unsigned overflow, truncated buffers.

## Key Files

- `MsgPackReader.java` — Core reader with typed read methods and `skipValue()`/`skipValues()` traversal
- `MsgPackWriter.java` — Core writer with typed write methods and static encoded-length calculators
- `MsgPackFormat.java` — 256-entry lookup table mapping wire bytes to format enum
- `MsgPackCodes.java` — Wire protocol constants and bit-mask predicates
- `MsgPackToken.java` — Mutable type-tagged value holder for generic token reading