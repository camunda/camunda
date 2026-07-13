```yaml
---
applyTo: "zeebe/msgpack-value/src/**"
---
```

# zeebe/msgpack-value — MsgPack Serialization Value Framework

## Purpose

This module provides a property-based, zero-copy MsgPack serialization framework for defining structured objects that can be serialized to/from MsgPack binary format. It is the foundation for all Zeebe engine record values (`UnifiedRecordValue` in `zeebe/protocol-impl`) and engine state objects (e.g., `ElementInstance`, `PersistedProcess` in `zeebe/engine`). The framework supports forward/backward-compatible serialization via undeclared property preservation.

## Architecture

Two-layer design:

1. **`value/` layer** — Low-level MsgPack-aware types that implement `read(MsgPackReader)`, `write(MsgPackWriter)`, `getEncodedLength()`, and `writeJSON(StringBuilder)`. Each type maps to a MsgPack format: `LongValue` → integer, `StringValue` → string, `BinaryValue` → binary, `DocumentValue` → binary (validated as MAP), `ObjectValue` → map, `ArrayValue<T>` → array, `PackedValue` → raw passthrough, `EnumValue<E>` → string, `DeltaEncodedLongArrayValue` → array with delta encoding.

2. **`property/` layer** — Named key-value wrappers (`BaseProperty<T extends BaseValue>`) that pair a `StringValue` key with a typed value and optional default. Properties handle serialization of the key-value pair, default resolution, and sanitization masking. One property class per value type: `LongProperty`, `StringProperty`, `DocumentProperty`, `ArrayProperty<T>`, `ObjectProperty<T>`, etc.

**Composition root**: `UnpackedObject` extends `ObjectValue` and implements `BufferReader`/`BufferWriter` from `zeebe-util`. Subclasses declare properties in their constructor via `declareProperty(prop)`, forming the schema. See `POJO.java` test fixture for the canonical pattern.

## Data Flow

**Writing**: `UnpackedObject.write(buffer, offset)` → `MsgPackWriter` → writes map header + each declared property's key-value pair + undeclared properties. Length is pre-calculable via `getEncodedLength()`.

**Reading**: `UnpackedObject.wrap(buffer, offset, length)` → `reset()` → `MsgPackReader` → reads map header → matches each key to declared properties (optimistic index cycling starting at position `i`), falls back to `UndeclaredProperty` for unknown keys → verifies all declared properties have values or defaults.

**Undeclared properties**: Unknown keys read during deserialization are stored as `UndeclaredProperty` (extends `PackedProperty`) and round-tripped on write. This provides forward compatibility — newer fields survive older code. Recycled via an object pool to avoid allocation.

## Key Abstractions

| Type | Role | File |
|------|------|------|
| `BaseValue` | Abstract root; defines `read`, `write`, `getEncodedLength`, `writeJSON`, `reset` | `value/BaseValue.java` |
| `ObjectValue` | MsgPack MAP with declared + undeclared property lists | `value/ObjectValue.java` |
| `UnpackedObject` | Top-level serializable object; implements `BufferReader`/`BufferWriter` | `UnpackedObject.java` |
| `BaseProperty<T>` | Named key-value pair with default/sanitization support | `property/BaseProperty.java` |
| `Recyclable` | Reset interface for object reuse without allocation | `Recyclable.java` |
| `ValueArray<T>` | Interface for iterable, `RandomAccess` typed arrays | `value/ValueArray.java` |
| `ArrayValue<T>` | Factory-based typed array using `Supplier<T>` | `value/ArrayValue.java` |
| `DocumentValue` | Binary value validated as MsgPack MAP; NIL → empty document | `value/DocumentValue.java` |
| `PackedValue` | Opaque raw MsgPack passthrough (no interpretation) | `value/PackedValue.java` |
| `DeltaEncodedLongArrayValue` | Space-optimized long array using delta encoding on wire | `value/DeltaEncodedLongArrayValue.java` |

## How to Define a New Serializable Object

1. Extend `UnpackedObject` (or `UnifiedRecordValue` for protocol records).
2. Declare property fields using the appropriate `*Property` type.
3. In the constructor, call `super(N)` where N is the number of declared properties, then chain `declareProperty(...)` for each.
4. Expose typed getters/setters that delegate to the property's `getValue()`/`setValue()`.

```java
public final class MyRecord extends UnpackedObject {
  private final LongProperty keyProp = new LongProperty("key");
  private final StringProperty nameProp = new StringProperty("name", "default");

  public MyRecord() {
    super(2);
    declareProperty(keyProp).declareProperty(nameProp);
  }

  public long getKey() { return keyProp.getValue(); }
  public void setKey(final long key) { keyProp.setValue(key); }
  public DirectBuffer getName() { return nameProp.getValue(); }
  public void setName(final String name) { nameProp.setValue(name); }
}
```

## Invariants

- Always pass the correct property count to `super(N)` to avoid list resizing.
- Every declared property must have a value set or a default after deserialization; otherwise `read()` throws.
- Property declaration order determines the optimistic key-matching offset during deserialization — declare in the same order as serialization for best performance.
- `reset()` must be called before reuse (done automatically by `UnpackedObject.wrap()`).
- `DocumentValue` rejects non-MAP root-level MsgPack; NIL and empty are normalized to `EMPTY_DOCUMENT`.
- `DeltaEncodedLongArrayValue` only delta-encodes on the MsgPack wire format; JSON output uses absolute values.
- Mark sensitive properties with `.sanitized()` to mask them in `toString()` and log output.
- All buffer references are zero-copy wraps via Agrona `DirectBuffer` — never copy bytes unless explicitly needed.

## Common Pitfalls

- Forgetting to call `declareProperty()` makes the field invisible to serialization.
- Passing wrong `expectedDeclaredProperties` count causes unnecessary `ArrayList` resizing.
- Reading a property without a value or default throws `MsgpackPropertyException` — always provide defaults for optional fields.
- `StringValue`/`BinaryValue` wrap the source buffer directly — if the source buffer is reused, the value becomes stale. Copy explicitly if the value must outlive the source.
- `EnumValue` uses `Enum.valueOf()` on deserialization — an unknown enum string causes `IllegalArgumentException`.
- `IntegerValue.read()` throws if the MsgPack integer exceeds `Integer.MAX_VALUE`; use `LongProperty` for values that may exceed 32 bits.

## Dependencies and Consumers

**Dependencies**: `zeebe-msgpack-core` (MsgPackReader/Writer/spec), `agrona` (DirectBuffer, UnsafeBuffer), `zeebe-util` (BufferReader/Writer, StringUtil).

**Primary consumers**: `zeebe/protocol-impl` (~90+ record value classes extending `UnifiedRecordValue`), `zeebe/engine` state classes (~60+ classes extending `UnpackedObject`), `zeebe/stream-platform`, `zeebe/broker`, `zeebe/backup`.

## Key Reference Files

- `UnpackedObject.java` — composition root, BufferReader/Writer bridge
- `value/ObjectValue.java` — declared/undeclared property management, read/write logic
- `property/BaseProperty.java` — key-value pair with defaults, sanitization, serialization
- `value/DocumentValue.java` — validated MsgPack MAP binary value
- `value/DeltaEncodedLongArrayValue.java` — space-optimized delta-encoded long array
- `test/.../POJO.java` — canonical example of defining an UnpackedObject subclass