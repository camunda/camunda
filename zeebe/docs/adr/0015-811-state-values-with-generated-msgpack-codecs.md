# State values are plain data classes with generated msgpack codecs

**DRI**: Carlo Sana

**Status**: Proposed (8.11)

**Purpose**: Defines how Zeebe state values and records are serialized to msgpack once they no
longer extend `UnpackedObject`, and which guarantees about the stored format the generated code
keeps.

**Audience**: Zeebe engineers who add or change state values or records, and reviewers of the
migration away from `UnpackedObject`.

## Context

Engine state values and protocol records extend `UnpackedObject`. The in-memory model and the
codec are one tree of `Property` objects that describes the class at run time. Every read walks
and changes these objects, so the hot read and write path of the state allocates and branches more
than plain data needs to, and every class carries code to register its properties.

The `zeebe/msgpack-codegen` module replaces this with plain data classes annotated with
`@MsgpackPojo`, whose codecs an annotation processor generates at compile time. The stored bytes
must not change: during a rollback and in a mixed-version cluster, each version reads what the
other version wrote.

## Decision

**D1. A state value is a plain data class with a codec that is generated at compile time.**
For each `@MsgpackPojo` class, the processor generates a stateless `<Name>Codec` (write, length,
read) and a `<Name>Ops` (reset, copy, equals, hashCode, toString). The fields are the model, and
the field initializers are the defaults. The build fails for a class that the generated code
cannot store correctly.

**D2. The stored format is the contract, and it is the format of `UnpackedObject`.**
The keys are the field names (or `@Key`), written in declaration order. A reader accepts any key
order, keeps the default for a missing key, and keeps unknown keys and writes them back, so a value
from a newer version survives a round trip through an older one. A property without a default
fails on write and on read in the same way as a legacy property that is not set. Generated and
legacy classes read each other's bytes.

**D3. A property without a default is a holder or a `@Nullable` field.**
A `long` or `int` without a default is a `RequiredLong` or `RequiredInt`. The other types are
`@Nullable`, and null means not set. No sentinel value means "not set", because a sentinel can be
equal to a real value.

**D4. `MsgpackBufferValue` connects the generated classes to the buffer interfaces for now.**
A class that must still be a `DbValue`, `BufferReader` or `BufferWriter` extends
`MsgpackBufferValue`, which reads and writes through a reader and a writer per thread. This bridge
is temporary. When it is removed is still open.

## Alternatives considered

- **Describe the class at run time.** Both keeping `UnpackedObject` and a codec that reads plain
  fields through reflection find the shape of the class at run time. Every read and write then
  goes through extra steps per field (property objects, or reflective access with boxing), and a
  class that cannot be stored is only found when it is used. Generated code accesses the fields
  directly, and the build fails instead.

## Consequences

- Each class that is migrated must follow the rules of the processor (fields that are not
  private, final containers, ...). The module README lists the rules.
- Reading a list of nested objects allocates one item per element, while `ArrayProperty` reuses
  its items ([#69305](https://github.com/camunda/camunda/issues/69305)).
- The format compatibility is guaranteed by tests, not by construction: a property test checks
  the generated code against an independent model of the format and against an `UnpackedObject`
  with the same properties. Each migration also needs its own compatibility tests.
- Each module that has `@MsgpackPojo` classes needs the annotation processor in its Maven and
  Gradle builds.
- `toString` prints the same JSON as `UnpackedObject`, so the logs stay comparable.

## Source

- [Replace UnpackedObject state classes with generated msgpack codecs](https://github.com/camunda/camunda/issues/69223)
- [feat: add module generating msgpack codecs for plain data classes](https://github.com/camunda/camunda/pull/69757)
- [`zeebe/msgpack-codegen` README](../../msgpack-codegen/README.md)

