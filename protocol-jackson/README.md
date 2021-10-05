# Jackson bindings for Zeebe protocol records

The goal of this module is to provide a basic implementation of the [Zeebe protocol](/protocol)
`Record` interface and its possible values, e.g. `VariableRecordValue`, `IncidentRecordValue`, etc.

This implementation can be used to (de)serialize these objects using Jackson (and therefore any
format supported by Jackson). Use cases here are mostly for exporters and Java applications that are
built around exporters, such as Operate, Tasklist, and Optimize.

Additionally, the module provides builders for each of these values, allowing them to be used as
standalone `Record` implementations. The main use case here is for testing, where it may be useful
to specific records.

## How it works

The module provides a set of abstract types and uses [Immutables](https://immutables.github.io) to
generate the actual implementation. To reduce API surface, the generated immutable classes are
hidden, and instead only the generated builders and the abstract stubs are exposed.

> NOTE: hiding the implementation classes is achieved via configuration in the `ZeebeStyle`
> annotation. See there for more comments on which options control what.

Serialization to and from the hidden generated types still work, as users can use the abstract
types, e.g. `AbstractVariableRecordValue`, as the type reference in Jackson. These abstract types
are annotated to tell Jackson what concrete type should be used for deserialization.

Serializing an immutable record would just be like serializing any POJO with Jackson:

```java
// assume a pre-defined Record<?> record object
final ObjectMapper mapper = new ObjectMapper();
final String myJson = mapper.writeValueAsString(record);
```

To deserialize the record back, you simply need to specify the abstract type, either as a class or
as a type reference.

```java
// assume a myJson string containing the JSON
final ObjectMapper mapper = new ObjectMapper();
final Record<?> record = mapper.readValue(myJson,AbstractRecord.class);

// record will be correctly deserialized with the right parametric type
```

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
`ValueTypes`, which maps a `ValueType` constant to a pair of `Intent` sub class and an appropriate
`RecordValue` sub class.

## Using the builders

As mentioned, one use case is in testing. It can be useful to manually create a series of records as
input for some test. You can use the generated builders to do so. Builders are named after their
abstract type, minus the `Abstract` prefix. This means `AbstractRecord` generates `RecordBuilder`,
`AbstractVariableRecordValue` generates `VariableRecordValueBuilder`, and so on.

The generated builders are plain old builders for beans. To use them, simply instantiate them, fill
out the attributes, and build the object.

```java
final VariableDocumentRecordValue value = new VariableDocumentRecordValueBuilder()
  .scopeKey(1)
  .semantics(VariableDocumentUpdateSemantic.PROPAGATE)
  .putVariables(Map.of())
  .build();
final RecordBuilder<?> builder = new RecordBuilder<>()
  .value(value)
  .valueType(ValueType.VARIABLE_DOCUMENT)
  .intent(VariableDocumentIntent.UPDATE)
  .recordType(RecordType.COMMAND)
  .key(1);

// apply more properties
final Record<?> record = builder.build();
```

> NOTE: some attributes are required and do not have a default value. The `#build` method will fail
> in this case with an appropriate error. This isn't fixed in stone, and if we want we can always
> add more sane defaults, but it was decided to go with this for now to simplify the initial
> iteration.

Also note that builders have a `#from` method generated which accepts all super types of the
abstract type. So for `VariableRecordValueBuilder`, there would be a `#from(VariableRecordValue)`
method generated which can accept any implementations of the interface. This only performs shallow
copying of values, meaning that when copying a `Record<?>`, it will simply set its value to the
reference of `Record#getValue()`, and will copy that into an immutable object itself. If you need
deep copy, you will have to manually copy all objects - or open an issue for it!

## Development

There are some unfortunate downsides to this approach when it comes to developing for Zeebe.

### Setup

While the Maven setup has already been taken care of, you may want to make sure that your IDE is
also correctly set up to work with annotation processors. See this
[page](https://immutables.github.io/apt.html) for more.

### Adding a new record value

If the `ValueType` enum is modified, then this has an impact on this module.

When adding a new `ValueType`, you will need to go through the following steps, in order.

First, add a new appropriate `Abstract*` value class which implements the equivalent interface. e.g.
you add `ValueType.ULTRA`, and the interface `UltraRecordValue`, then you need to add a new
`AbstractUltraRecordValue` to this module, with the following template:

```java
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.protocol.jackson.record.UltraRecordValueBuilder.ImmutableUltraRecordValue;
import io.camunda.zeebe.protocol.record.value.UltraRecordValue;
import org.immutables.value.Value;

@Value.Immutable
@ZeebeStyle
@JsonDeserialize(as = ImmutableUltraRecordValue.class)
public abstract class AbstractUltraRecordValue
  implements UltraRecordValue, DefaultJsonSerializable {

}
```

The annotation `@Value.Immutable` tells the generator that a new immutable class should be generated
from this abstract type. The `@ZeebeStyle` annotation configures the generation of that class. We
use a centralized configuration annotation to ensure all generated types are aligned. Finally, the
abstract class has to implement the correct interface, and we also implement
`DefaultJsonSerializable` to provide a default implementation of `toJson`.

After this, you need to add a new mapping in the `ValueTypes#TYPES` map, mapping the new `ValueType`
constant with its intent class and the new immutable value class, e.g.:

```java
final class ValueTypes {

  private static final Map<ValueType, ValueTypeInfo<?>> TYPES;

  static {
    final Map<ValueType, ValueTypeInfo<?>> mapping = new EnumMap<>(ValueType.class);

    mapping.put(
      ValueType.DEPLOYMENT,
      new ValueTypeInfo<>(ImmutableDeploymentRecordValue.class, DeploymentIntent.class));

    // ... more mappings
    mapping.put(
      ValueType.ULTRA,
      new ValueTypeInfo<>(ImmutableUltraRecordValue.class, UltraIntent.class));
  }
}
```

> NOTE: there are two smoke tests which will ensure that we can map all `ValueType` constants to a
> concrete `Intent` sub-class and a concrete `RecordValue` sub-class, just to be safe.

This is the basic step, and what you most likely will have to do in most cases. There is one
exception to this case: if you have nested abstract types which also need to be generated. In order
for Jackson to properly deserialize these abstract types (whether abstract classes or interfaces),
we need to specify to Jackson the concrete type it should use.

Let's take a look at two existing types with nested types: the `JobBatchRecordValue`, and the
`DeploymentRecordValue`.

The `JobBatchRecordValue` has a list of `JobRecordValue`. `JobRecordValue` is also one of the types
that we generate, and in order for deserialization to work, we need to tell Jackson the type into
which it should be deserialized. In this case, there is no polymorphism, so this is simpler than
with the `Record` interface: we can simply use
`JsonDeserialize(contentAs = ImmutableJobRecordValue.class)`.

> NOTE: we use `contentAs` and not `as`, since we specify the type of the list entries, not of the
> list itself.

We do the same for the `DeploymentRecordValue`. Here we even had to create custom abstract types for
the `DeploymentResource`. We do the same as with any other type that we want to generate, except
this time it doesn't need to implement `DefaultJsonSerializable`, and we don't need to provide a
value type mapping for it (as `DeploymentResource` is not a value type).

### Default values

At times, you may want to specify sane defaults for your generated types. You can do so by
annotating abstract methods with `@Value.Default`. These values are set in the generated builders
on `#build()` if and only if no other value was provided - meaning, if the user provides `null` as a
value, then the default value is not used but `null` is used instead.

You can see an example of its usage in `AbstractRecord`, where specify some sane defaults for enums.
As a rule of thumb, avoid providing a default value unless there is an obvious one.

> NOTE: you do _not_ need to provide defaults for collections. These receive by default an
> appropriate empty collection and will not be null.

### Further reading

If you want to do something that isn't covered here, I suggest reading the
[Immutables](https://immutables.github.io/immutable.html) documentation, which is pretty thorough.
The library has even more features we aren't touching here.
