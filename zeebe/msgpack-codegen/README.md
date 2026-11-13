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
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  // nothing else: FooCodec and FooResetter are generated and found by MsgpackBufferValue
}
```

## How it works

- The annotation processor generates a stateless `<Name>Codec implements MsgpackCodec<Name>` per
  `@MsgpackPojo` (`write`, `getLength`, `read`). The caller owns the `MsgPackWriter`/`MsgPackReader`.
- Defaults are the field initializers. The processor also generates a `<Name>Resetter implements
  MsgpackResetter<Name>`, which copies them from a prototype instance (plain field assignments, no
  reflection). It is separate from the codec.
- Resetting belongs to the class: it must be `Recyclable`, and `reset()` is called before every
  read, so properties missing in the input keep their default. `MsgpackBufferValue` implements it by
  calling the generated resetter. `MsgpackCodec<T extends Recyclable>` and the processor make a class
  that is not `Recyclable` fail compilation.
- Every non-static field is stored under its field name, in declaration order, unless it is
  `@MsgpackIgnore`d. `@MsgpackProperty("key")` overrides the key or marks a `raw` value. The key
  names and their order are the stored format.
- Supported field types: `long`, `int`, `boolean`, `DirectBuffer`/`UnsafeBuffer` (string, binary,
  raw msgpack, or a document), enums (stored by `toString()`), `List<DirectBuffer>`, `List<Long>`, agrona's
  `IntArrayList`/`LongArrayList` (unboxed, prefer them over `List<Long>`), other
  `@MsgpackPojo`s and `BaseValue`s.
- `@MsgpackProperty(raw = true)` embeds an already encoded msgpack value and replaces
  `PackedProperty`. `@MsgpackProperty(document = true)` replaces `DocumentProperty`: it is stored as
  a msgpack binary that holds a map, a nil or empty value is read as the empty map and anything else
  that is not a map is rejected. The usual default is `DocumentValue.EMPTY_DOCUMENT`, which, like
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
- A key missing in the input keeps the default. `@MsgpackProperty(required = true)` makes reading
  fail instead ("Property 'key' has no valid value"), like a property of an `UnpackedObject`
  without default. Use it for a property that had no default in the class it replaces: a
  primitive always has a Java default, so without it a missing key is read as `0` or `false`. An
  enum field without initializer is required without the annotation, as it holds no value then.
- String, binary and raw fields need an initializer. The resetter assigns (or wraps) the default of
  a shared prototype instance, so a default buffer must never be modified in place.
- Codecs and resetters are found with `Codecs.of(Type.class)` and `Codecs.resetterOf(Type.class)`
  (`ClassValue`s, by naming convention). Generated code resolves its nested codecs once, when the
  generated class is initialized.
- Keys of a newer version that this class does not declare are kept in `UnknownProperties` and
  written back, so a read-modify-write by an older version does not drop them. Every
  `@MsgpackPojo` needs exactly one `final` `@MsgpackUnknownProperties UnknownProperties` field,
  like `UnpackedObject`, which always keeps them; compilation fails otherwise.
- Strings, binaries and raw values are read as views on the source buffer, like `StringProperty`,
  `BinaryProperty` and `PackedProperty`: nothing is copied, so they are only valid while the source
  buffer is not reused. Nested `BaseValue`s are read by reference to it as well.
- `MsgpackBufferValue` is a temporary bridge for classes that still have to be a `DbValue`. It uses
  the per-thread reader and writer of `MsgpackScratch`.

Benchmark: `microbenchmarks/.../msgpack/StateValueBenchmark`, comparing `UnpackedObject`s with generated codecs.
