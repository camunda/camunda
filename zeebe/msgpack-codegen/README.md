# Zeebe Msgpack Codegen

Generates msgpack codecs for plain data classes, so that state values do not have to be
`UnpackedObject`s (where the in-memory model and the codec are the same property tree).

```java
@MsgpackPojo
public final class Foo extends MsgpackBufferValue<Foo> implements DbValue {
  long key = -1L;                         // the default is the field initializer
  DirectBuffer name = Decoding.EMPTY;     // msgpack string
  final List<DirectBuffer> ids = new ArrayList<>();
  final Bar bar = new Bar();              // another @MsgpackPojo, or a BaseValue
  final UnknownProperties unknownProperties = new UnknownProperties();

  @Override protected MsgpackCodec<Foo> codec() { return FooCodec.INSTANCE; }
  @Override protected MsgpackOps<Foo> ops() { return FooOps.INSTANCE; }
  // nothing else: FooCodec and FooOps are generated
}
```

## How it works

- The annotation processor generates a stateless `<Name>Codec implements MsgpackCodec<Name>` per
  `@MsgpackPojo` (`write`, `getLength`, `read`). The caller owns the `MsgPackWriter`/`MsgPackReader`.
- Defaults are the field initializers. The processor also generates a `<Name>Ops` with the other
  operations on the class: `reset`, which copies the defaults from a prototype instance (plain field
  assignments, no reflection), `copy`, `equals`, `hashCode` and `toString`. The operations are static methods, so generated
  code calls them directly where the type is known; `NameOps.INSTANCE` implements `MsgpackOps<Name>`
  for code that is generic in the type. It is separate from the codec.
- Resetting belongs to the class: it must be `Recyclable`, and `reset()` is called before every
  read, so properties missing in the input keep their default. `MsgpackBufferValue` implements it by
  calling the generated ops. `MsgpackCodec<T extends Recyclable>` and the processor make a class
  that is not `Recyclable` fail compilation.
- `@MsgpackPojo` and the field annotations are in the package
  `io.camunda.zeebe.msgpack.pojo.annotation`. Import them with
  `import io.camunda.zeebe.msgpack.pojo.annotation.*;`.
- The codec stores every non-static field under its field name, in declaration order. `@Ignore`
  excludes a field. `@Key("key")` sets a different key. The key names and their order are the
  stored format.
- Supported field types: `long`, `int`, `boolean`,
  `RequiredLong`/`RequiredInt` and `@Nullable Long`/`Integer`/`Boolean` (no default, see below), `DirectBuffer`/`UnsafeBuffer` (string, binary,
  raw msgpack, or a document), enums (stored by `toString()`, read by `name()`, so `toString()` must
  not be overridden, and no constant may have a body), `List<DirectBuffer>`, `List<Long>`, `List<Integer>` (read with the range
  check of an `int`), lists of these lists to any depth (`List<List<Long>>`, ..., which start empty, as
  the generated class checks on reset: the inner lists would be shared by every reset), agrona's
  `IntArrayList`/`LongArrayList` (unboxed, prefer them over `List<Long>`), other
  `@MsgpackPojo`s and `BaseValue`s, and lists of those (`List<Item>`, replacing an `ArrayProperty` of
  objects, also nested: `List<List<Item>>`).
- `@Raw` embeds an already encoded msgpack value and replaces `PackedProperty`.
- `@Document` replaces `DocumentProperty`. The codec stores the document as a msgpack binary that
  holds a map. It reads a nil or empty value as the empty map, and it rejects any other value that
  is not a map. The usual default is `DocumentValue.EMPTY_DOCUMENT`, which, like
  every default buffer, must never be modified. A setter that has to keep the guarantee of
  `DocumentProperty#setValue` calls `Decoding.document(buffer)`, which normalizes and validates.
- A `List<Item>` of `@MsgpackPojo`s or `BaseValue`s, or a list of such lists, is a `final` field
  that starts empty (the generated class checks it on reset: the items are owned by one list, so a
  default with items would be shared). It is stored as a msgpack array of maps, like an `ArrayProperty`.
  `Item` needs a public constructor without parameters. Unlike an `ArrayProperty`, which recycles
  its items, reading allocates one item per element, because an `Item` is created for every element
  read; a field that is read on a hot path with many items should be weighed against that.
- The class must be top-level, its superclasses must not declare stored fields, and keys must be
  unique; compilation fails otherwise.
- Fields are read and written directly, so they must not be `private`. Scalars, strings and enums
  must not be `final`, nested objects and lists must be. An `UnsafeBuffer` is the exception: it
  must be `final`, as it is wrapped in place on read, so reading does not allocate (a
  `DirectBuffer` field gets a new view per read, and so does every element of a list). A non-final
  one would be the shared default after a reset, so wrapping it would change the default.
  Compilation fails if a field breaks these rules.
- A key missing in the input keeps the default.
- A property without a default, like a legacy property declared without one, has an unset state
  and no initializer. A `long` or `int` is a `final RequiredLong`/`RequiredInt` field: a small
  mutable holder with `set(v)`, `get()`, `isSet()` and `reset()`, which does not allocate on read
  and has no sentinel value that a real value could clash with. A string, binary, raw, document,
  enum or `Boolean` is a `@Nullable` field (jspecify) that starts as `null`; `null` is unset. A
  `@Nullable Long` or `Integer` is supported as well, an alternative to the holders that is written
  like a `long`/`int` but allocates when it is read (the holders do not). A `boolean`, `long` or
  `int` with a default stays a primitive: the boxed types are only for one without a default.
- The processor rejects `@Nullable` on a primitive, on a holder, on an `UnsafeBuffer` or on a nested
  value, a holder that is not `final`, and a `Boolean`, `Long` or `Integer` that is not
  `@Nullable`.
- A class that has properties without a default is `@NullMarked`, and the fields of those properties
  are `@Nullable`. Their getters are non-null and return `Decoding.required(field, "key")`, so
  NullAway forces the check and an unset value fails with the `MsgpackPropertyException` of the
  `getValue()` of a legacy property ("Expected a value or default value to be specified, but has
  nothing").
- The unset properties behave like those of an `UnpackedObject`: `write` and `getLength` fail with
  the `MsgpackPropertyException` of the first one that is unset ("Expected a value or default
  value to be set before writing, but has nothing", and "... to be specified, ..." for the
  length), before anything is written. `read` fails with "Property 'key' has no valid value" if
  the key is missing (`reset` makes every one unset again), and `get()` of an unset holder fails
  with an `IllegalStateException` with the message of the legacy `getValue`. `toString` leaves an
  unset property out and keeps its comma, like `ObjectValue#toString`, which is not valid JSON.
  `copyFrom` copies the unset state too. `equals` fails with the `MsgpackPropertyException` of the
  legacy `getValue()` if a property of either value is unset (not if both are the same instance),
  like `UnpackedObject#equals`; `hashCode` does not fail.
- A string, binary and raw field that is not `@Nullable` needs an initializer. `reset` assigns (or
  wraps) the default of a shared prototype instance, so a default buffer must never be modified in
  place. An enum that is not `@Nullable` needs one too; if it is `null` anyway, `write` and
  `getLength` fail like for an unset property.
- Generated code finds the codecs of its nested values with `Codecs.of(Type.class)`
  (a `ClassValue`, by naming convention) once, when the generated class is initialized. A
  `MsgpackBufferValue` does no look-up: it declares abstract `codec()` and `ops()`, which every
  POJO overrides to return `FooCodec.INSTANCE` and `FooOps.INSTANCE`. They must not be called from
  the constructor: `FooOps` creates its defaults with `new Foo()` in its static initializer.
- `copyFrom(source)` of `MsgpackBufferValue` (`NameOps.copy(source, target)`) is a deep copy of the
  stored properties: the target loses its previous ones, shares nothing with the source, and does
  not depend on the buffer the source was read from. It allocates a copy of every string, binary,
  raw value and document, and one object per item of a list of nested objects. A nested value that
  is not a POJO and has no copy of its own is copied by writing it to a new array and reading it
  back. `@Ignore`d fields are not copied: a class with such state overrides `copyFrom` and
  calls `super`.
- `equals` and `hashCode` of `MsgpackBufferValue` (`NameOps.equals`/`hashCode`) compare the stored
  properties by content, like `UnpackedObject` does: buffers by their bytes, lists by
  their elements, unknown properties by their bytes. A nested `BaseValue` is compared with its
  own `equals`, like `UnpackedObject` does. `hashCode` does not look inside a nested
  `BaseValue` (an `UnpackedObject`), only at the size of a list of them: their own `hashCode` is not
  consistent with their `equals`, and equal values must hash alike. Unlike `UnpackedObject`, an instance is
  only equal to one of exactly the same class. `@Ignore`d fields are not compared: a class with such
  state overrides both methods.
- `toString` of `MsgpackBufferValue` prints the stored properties as JSON in the format of
  `UnpackedObject`: `{"key":value,...}` in declaration order, strings and enums quoted, binaries and
  documents as quoted base64, raw values as `[packed value (length=n)]`, and the entries of unknown
  properties last, in the same form. Unlike `UnpackedObject`, which leaves out the comma before the
  first unknown entry, the result is valid JSON. A field annotated `@Sanitized` prints
  `"***"` instead of its value, also when its POJO is nested in another one (a nested
  `UnpackedObject` only masks at the top level). The stored format is not affected.
- Keys of a newer version that this class does not declare are unknown properties. The class keeps
  them in `UnknownProperties`, and the codec writes them back. Thus a read-modify-write by an older
  version does not drop them. `UnpackedObject` also keeps them. By default, a `@MsgpackPojo` must
  have exactly one `final` field of the type `UnknownProperties`. The processor finds this field by
  its type. Compilation fails if the class has no such field, more than one, or one that is not
  `final`.
- `@MsgpackPojo(unknownProperties = false)` makes the codec skip unknown properties when it reads.
  Thus it does not write them back. The class must not have an `UnknownProperties` field.
  Compilation fails otherwise. Use this setting only for a value that never has keys of a newer
  version.
- Strings, binaries and raw values are read as views on the source buffer, like `StringProperty`,
  `BinaryProperty` and `PackedProperty`: nothing is copied, so they are only valid while the source
  buffer is not reused. Nested `BaseValue`s are read by reference to it as well.
- `MsgpackBufferValue` is a temporary bridge for classes that still have to be a `DbValue`. It uses
  the per-thread reader and writer of `MsgpackScratch`.

## Writing a new class

- Wire the module once: Gradle needs `implementation(project(":zeebe-msgpack-codegen"))` and
  `annotationProcessor(project(":zeebe-msgpack-codegen"))`, Maven the `zeebe-msgpack-codegen`
  dependency and an entry in the `annotationProcessorPaths` of the compiler plugin. The
  `microbenchmarks` module shows both. The generated `FooCodec` and `FooOps` are in
  `build/generated/sources/annotationProcessor/java/main/` (Gradle); `FooCodec` declares the keys
  in the stored order.
- Field initializers must not have side effects, and the class should not need a constructor. If it
  has one, it must not call `reset`, `copyFrom`, `equals`, `hashCode`, `toString`, `codec()` or
  `ops()`: `FooOps` creates its defaults with `new Foo()` in its static initializer, so such a call
  throws a `NullPointerException` there, and every later use of the class fails with
  `NoClassDefFoundError`. A no-argument constructor must stay reachable from the package.
- A class with `@Ignore`d state overrides `reset()` as `super.reset()` followed by the reset
  of that state, like `copyFrom`, `equals` and `hashCode`.
- For a `long` or `int` without a default, use a `RequiredLong`/`RequiredInt` holder.
- A `@MsgpackPojo` is not a `BaseValue`, so an `UnpackedObject` cannot hold it in an
  `ObjectProperty` or `ArrayProperty`. The class that holds it must be a `@MsgpackPojo` too.

Benchmark: `microbenchmarks/.../msgpack/StateValueBenchmark`, comparing `UnpackedObject`s with generated codecs.
