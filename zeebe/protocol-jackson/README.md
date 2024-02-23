# Serialization of the Zeebe protocol record and value types

The goal of this module is to provide deserialization support of the [Zeebe protocol](/protocol)
`Record` interface and its possible values, e.g. `VariableRecordValue`, `IncidentRecordValue`, etc.,
as well as their nested types.

## Usage

In order to use it, simply register the `ZeebeProtocolModule` to your `ObjectMapper` or
`ObjectReader`:

```java
final ObjectMapper mapper = new ObjectMapper().registerModule(new ZeebeProtocolModule());
final String myJson = ...;
final Record<?> record = mapper.readValue(myJson, new TypeReference<Record<?>> {});
```

## How it works

The module will register a concrete sub-type for each interface types from the protocol, e.g.
`ImmutableRecord` is registered as the concrete type for `Record`, `ImmutableJobRecordValue` for
`JobRecordValue`, and so on.

As these are immutable objects and not beans, by default, Jackson will not know how to create them.
The `BuilderAnnotationIntrospector` is added to tell Jackson to use this builder as if the type had
been  annotated with `JsonPOJOBuilder`.

Finally, to map the `Record`'s intent and value based off on the `valueType` property, we register
an annotation mixin, `RecordMixin`, which allows us to annotate third-party types.

### Polymorphic deserialization

Since the main `Record` interface is parameterized over the value property, this means we have to
figure out concrete type of the `value` object at deserialization time. The protocol defines this
via the `valueType` property. This is an enum which defines the expected type of the `value` at
runtime.

To achieve this, the first step is telling Jackson that the concrete type of the `value` property is
resolved by its sibling property, `valueType`. This is achieved via the `JsonTypeInfo` annotation.
Then we provide a custom `TypeIdResolver` (`ValueTypeIdResolver` and `IntentTypeIdResolver`) which
provide Jackson with the concrete type from the given value type during deserialization.

To support this and avoid having to map the value type multiple times, we have a central mapping in
`ValueTypes`, which maps a `ValueType` constant to a pair of `Intent` subclass and an appropriate
`RecordValue` sub class.
