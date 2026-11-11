# Zeebe Msgpack Codegen

Generates msgpack codecs for plain data classes, so that state values do not have to be
`UnpackedObject`s (where the in-memory model and the codec are the same property tree).

```java
@MsgpackPojo
public final class Foo extends MsgpackBufferValue<Foo> implements DbValue {
  long key;
  DirectBuffer name;                      // msgpack string
  final List<DirectBuffer> ids = new ArrayList<>();
  final Bar bar = new Bar();              // another @MsgpackPojo, or a BaseValue
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public Foo() {
    reset();
  }

  /** The only place with the defaults. Called before every read. */
  @Override
  public void reset() {
    key = -1L;
    name = Decoding.EMPTY;
    ids.clear();
    bar.reset();
    unknownProperties.clear();
  }

  @Override
  protected MsgpackCodec<Foo> codec() {
    return FooCodec.INSTANCE;             // generated
  }
}
```

## How it works

- The annotation processor generates a stateless `<Name>Codec implements MsgpackCodec<Name>` per
  `@MsgpackPojo` (`write`, `getLength`, `read`). The caller owns the `MsgPackWriter`/`MsgPackReader`.
- Resetting is the job of the class: it must be `Recyclable`, its `reset()` restores the default of
  every property and is called before every read, so properties missing in the input keep their
  default. `MsgpackCodec<T extends Recyclable>` and the processor make a class that is not
  `Recyclable` fail compilation.
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
