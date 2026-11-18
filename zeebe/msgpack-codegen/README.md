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
  assignments, no reflection). The operations are static methods, so generated code calls them
  directly where the type is known; `NameOps.INSTANCE` implements `MsgpackOps<Name>` for code that
  is generic in the type. It is separate from the codec.
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
- Supported field types: `long`, `int`, `boolean`, `DirectBuffer`/`UnsafeBuffer` (string, binary,
  raw msgpack, or a document), enums (stored by `toString()`, read by `name()`, so `toString()` must
  not be overridden), `List<DirectBuffer>`, `List<Long>`, `List<Integer>` (read with the range
  check of an `int`), lists of these lists to any depth (`List<List<Long>>`, ...), agrona's
  `IntArrayList`/`LongArrayList` (unboxed, prefer them over `List<Long>`), other
  `@MsgpackPojo`s and `BaseValue`s.
- `@Raw` embeds an already encoded msgpack value and replaces `PackedProperty`.
- `@Document` replaces `DocumentProperty`. The codec stores the document as a msgpack binary that
  holds a map. It reads a nil or empty value as the empty map, and it rejects any other value that
  is not a map. The usual default is `DocumentValue.EMPTY_DOCUMENT`, which, like
  every default buffer, must never be modified. A setter that has to keep the guarantee of
  `DocumentProperty#setValue` calls `Decoding.document(buffer)`, which normalizes and validates.
- The class must be top-level, its superclasses must not declare stored fields, and keys must be
  unique; compilation fails otherwise.
- Fields are read and written directly, so they must not be `private`. Scalars, strings and enums
  must not be `final`, nested objects and lists must be. An `UnsafeBuffer` is the exception: it
  must be `final`, as it is wrapped in place on read, so reading does not allocate (a
  `DirectBuffer` field gets a new view per read, and so does every element of a list). A non-final
  one would be the shared default after a reset, so wrapping it would change the default.
  Compilation fails if a field breaks these rules.
- A key missing in the input keeps the default. If an enum field has no initializer and the input
  does not have its key, the read fails ("Property 'key' has no valid value").
- String, binary and raw fields need an initializer. `reset` assigns (or wraps) the default of
  a shared prototype instance, so a default buffer must never be modified in place.
- Generated code finds the codecs of its nested values with `Codecs.of(Type.class)`
  (a `ClassValue`, by naming convention) once, when the generated class is initialized. A
  `MsgpackBufferValue` does no look-up: it declares abstract `codec()` and `ops()`, which every
  POJO overrides to return `FooCodec.INSTANCE` and `FooOps.INSTANCE`. They must not be called from
  the constructor: `FooOps` creates its defaults with `new Foo()` in its static initializer.
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
  has one, it must not call `reset`, `codec()` or `ops()`: `FooOps` creates its defaults with `new Foo()` in its static initializer, so such a call
  throws a `NullPointerException` there, and every later use of the class fails with
  `NoClassDefFoundError`. A no-argument constructor must stay reachable from the package.
- A class with `@Ignore`d state overrides `reset()` as `super.reset()` followed by the reset
  of that state.
- A `@MsgpackPojo` is not a `BaseValue`, so an `UnpackedObject` cannot hold it in an
  `ObjectProperty` or `ArrayProperty`. The class that holds it must be a `@MsgpackPojo` too.
