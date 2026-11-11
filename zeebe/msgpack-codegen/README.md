# Zeebe Msgpack Codegen

Generates msgpack codecs for plain data classes, so that state values do not have to be
`UnpackedObject`s (where the in-memory model and the codec are the same property tree).

```java
@MsgpackPojo
public final class Foo extends MsgpackBufferValue<Foo> implements DbValue {
  long key = -1L;                         // default = field initializer
  DirectBuffer name = Decoding.EMPTY;     // msgpack string
  final List<DirectBuffer> ids = new ArrayList<>();
  final Bar bar = new Bar();              // another @MsgpackPojo, or a BaseValue
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  protected MsgpackCodec<Foo> codec() {
    return FooCodec.INSTANCE;             // generated
  }
}
```

## How it works

- The annotation processor generates a stateless `<Name>Codec implements MsgpackCodec<Name>` per
  `@MsgpackPojo` (`write`, `getLength`, `reset`, `read`, `decode`). The caller owns the
  `MsgPackWriter`/`MsgPackReader`.
- Every non-static field is stored under its field name, in declaration order, unless it is
  `@MsgpackIgnore`d. `@MsgpackProperty("key")` overrides the key or marks a `raw` value. The key
  names and their order are the stored format.
- Supported field types: `long`, `int`, `boolean`, `DirectBuffer` (string, or raw msgpack), enums
  (stored by `toString()`), `List<DirectBuffer>`, other `@MsgpackPojo`s and `BaseValue`s.
- Fields are read and written directly, so they must not be `private`. Scalars, strings and enums
  must not be `final`, nested objects and lists must be.
- Nested codecs are found with `Codecs.of(Type.class)` (a `ClassValue`) once, when the generated
  class is initialized.
- Keys of a newer version that this class does not declare are kept in `UnknownProperties` and
  written back, so a read-modify-write by an older version does not drop them.
- Strings and raw values are copied out of the source buffer. Nested `BaseValue`s are read by
  reference to it.
- `MsgpackBufferValue` is a temporary bridge for classes that still have to be a `DbValue`. It uses
  the per-thread reader and writer of `MsgpackScratch`.

Migrating a class: see the `migrate-state-to-msgpack-pojo` skill. Open work: [TODO.md](TODO.md).
