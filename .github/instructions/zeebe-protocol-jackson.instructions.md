```yaml
---
applyTo: "zeebe/protocol-jackson/**"
---
```
# Zeebe Protocol Jackson Module

## Purpose

This module provides Jackson (de)serialization support for the Zeebe protocol's `Record` interface and all `RecordValue` subtypes. It bridges the immutable, annotation-generated protocol types (from `zeebe/protocol`) to Jackson's `ObjectMapper` via a custom `Module`, annotation mixins, and polymorphic type resolvers. It targets Java 8 for broad client compatibility.

## Architecture

The module contains exactly 6 source files forming a layered Jackson extension:

```
ZeebeProtocolModule          ← Entry point: Jackson Module registered on ObjectMapper
├── RecordMixin              ← Annotation mixin for polymorphic intent/value resolution
├── AnnotationIntrospector   ← Finds builders for @ImmutableProtocol types, ignores unknown props
├── AbstractValueTypeIdResolver  ← Base TypeIdResolver: maps ValueType enum → concrete Java type
│   ├── RecordValueTypeIdResolver  ← Resolves RecordValue subtypes via ValueTypeMapping
│   └── IntentTypeIdResolver       ← Resolves Intent subtypes via ValueTypeMapping
```

## Key Abstractions

- **`ZeebeProtocolModule`** (`ZeebeProtocolModule.java`): The single public API. Registers `RecordMixin` on `ImmutableRecord.Builder`, `ImmutableCommandDistributionRecordValue.Builder`, `ImmutableAsyncRequestRecordValue.Builder`, and `ImmutableNestedRecordValue.Builder`. Inserts the custom `AnnotationIntrospector` into the Jackson pipeline.

- **`RecordMixin`** (`RecordMixin.java`): Jackson annotation mixin that drives polymorphic deserialization. Uses `@JsonTypeInfo(use = Id.CUSTOM, include = As.EXTERNAL_PROPERTY, property = "valueType")` on four fields: `intent`, `value`, `commandValue`, and `recordValue`. The `valueType` JSON property acts as the type discriminator for its sibling fields. Also uses `@JsonSetter(nulls = Nulls.SKIP)` for the `agent` field.

- **`AnnotationIntrospector`** (`AnnotationIntrospector.java`): Extends `NopAnnotationIntrospector`. Does three things: (1) enables `ignoreUnknown` for all types annotated with `@ImmutableProtocol.Type` or `@ImmutableProtocol.Builder` for forward compatibility, (2) resolves setter conflicts in immutable builders by preferring direct-value setters over `Consumer`-based setters, (3) finds the inner `Builder` class via `@ImmutableProtocol` or `@ImmutableProtocol.Type` annotations (simulating `@JsonPOJOBuilder`).

- **`AbstractValueTypeIdResolver`** (`AbstractValueTypeIdResolver.java`): Base class for `TypeIdResolverBase`. Converts `ValueType` enum names to/from type IDs. Subclasses implement `mapFromValueType(ValueType)` to return the concrete Java type.

- **`RecordValueTypeIdResolver`** / **`IntentTypeIdResolver`**: Concrete resolvers that delegate to `ValueTypeMapping.get(valueType)` to obtain `Mapping.getValueClass()` or `Mapping.getIntentClass()` respectively.

## Dependencies and Consumers

- **Depends on**: `zeebe-protocol` (the `Record`, `RecordValue`, `Intent`, `ValueType`, `ValueTypeMapping`, `ImmutableProtocol` annotations — all generated immutable types)
- **Consumed by** (all test scope except tasklist-qa-util): `zeebe-opensearch-exporter`, `zeebe-elasticsearch-exporter`, `zeebe-qa-integration-tests`, `optimize-backend`, `tasklist-qa-util`
- **Java target**: Java 8 (`<version.java>8</version.java>` in POM) — do NOT use Java 9+ APIs

## Polymorphic Deserialization Flow

1. JSON contains `"valueType": "JOB"` alongside `"value": {...}` and `"intent": "CREATED"`
2. Jackson encounters `RecordMixin`'s `@JsonTypeInfo` on `value` field → reads `valueType` as external property
3. `RecordValueTypeIdResolver.typeFromId("JOB")` → `ValueTypeMapping.get(ValueType.JOB).getValueClass()` → returns `ImmutableJobRecordValue`
4. `IntentTypeIdResolver.typeFromId("JOB")` → `ValueTypeMapping.get(ValueType.JOB).getIntentClass()` → returns `JobIntent`
5. `AnnotationIntrospector.findPOJOBuilder()` locates the inner `Builder` class on the immutable type
6. Jackson constructs the immutable object via the builder

## Extension Points

### Adding support for a new ValueType
When a new `ValueType` is added to `zeebe/protocol`:
1. Define the new `*RecordValue` interface and `*Intent` enum in `zeebe/protocol`
2. Annotate them with `@ImmutableProtocol` so immutable types are generated
3. Register the mapping in `ValueTypeMapping` (in `zeebe/protocol`)
4. **No changes needed in this module** — the resolvers dynamically read `ValueTypeMapping`

### Adding a new nested Record-like type
If a new type contains polymorphic `value`/`intent`/`commandValue`/`recordValue` fields resolved by `valueType`:
1. Add `setMixInAnnotation(ImmutableNewType.Builder.class, RecordMixin.class)` in `ZeebeProtocolModule` constructor
2. See existing pattern: `ImmutableCommandDistributionRecordValue.Builder`, `ImmutableAsyncRequestRecordValue.Builder`, `ImmutableNestedRecordValue.Builder`

## Invariants

- Every `ValueType` returned by `ValueTypeMapping.getAcceptedValueTypes()` must resolve to both a `RecordValue` subclass and an `Intent` subclass — tests enforce this exhaustively via parameterized tests
- Unknown JSON properties must be silently ignored for all `@ImmutableProtocol.Type` annotated classes (forward compatibility)
- Setter conflicts in immutable builders must resolve in favor of direct-value setters over `Consumer`-based setters
- The `RecordMixin` is applied to **builder** classes (not the immutable types themselves) because Jackson deserializes via builders

## Common Pitfalls

- **Do not use Java 9+ features**: This module targets Java 8 for client SDK compatibility
- **Do not modify this module when adding new ValueTypes**: The resolvers use `ValueTypeMapping` dynamically; changes belong in `zeebe/protocol`
- **Mixin targets builders, not types**: `setMixInAnnotation` must target the `Builder` inner class, not the parent immutable type
- **Test with `ProtocolFactory`**: Use `zeebe-protocol-test-util`'s `ProtocolFactory` to generate randomized protocol objects for round-trip serialization tests
- **Round-trip equality**: Tests use `RecordAssert.assertThat(deserialized).isEqualTo(original)` from `zeebe-protocol-asserts` — always verify full equality, not just individual fields

## Testing

- **`ZeebeProtocolModuleTest`**: Parameterized over all `ValueType`s — tests round-trip serialize/deserialize of `Record` objects and verifies unknown property tolerance on both `Record` and `RecordValue` levels
- **`RecordValueTypeIdResolveTest`**: Smoke test ensuring every `ValueType` resolves to a `RecordValue` subclass
- **`IntentTypeIdResolverTest`**: Smoke test ensuring every `ValueType` resolves to an `Intent` subclass
- Run: `./mvnw -pl zeebe/protocol-jackson -am test -DskipITs -DskipChecks -T1C`

## Key Files

| File | Role |
|------|------|
| `src/main/java/.../ZeebeProtocolModule.java` | Public API — the Jackson module to register |
| `src/main/java/.../RecordMixin.java` | Polymorphic type annotation mixin |
| `src/main/java/.../AnnotationIntrospector.java` | Builder discovery + unknown property tolerance |
| `src/main/java/.../AbstractValueTypeIdResolver.java` | Base TypeIdResolver for ValueType dispatch |
| `zeebe/protocol/.../ValueTypeMapping.java` | Central ValueType→(RecordValue, Intent) mapping (upstream) |