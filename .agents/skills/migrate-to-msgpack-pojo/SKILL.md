---
name: migrate-to-msgpack-pojo
description: Use when converting a class built from XxxProperty fields (an engine state class that extends UnpackedObject and implements DbValue, or a record value in zeebe/protocol-impl that extends UnifiedRecordValue) into a plain @MsgpackPojo data class with a generated codec, keeping the serialized bytes identical. One class per run.
---

# Migrate a property-tree class to a `@MsgpackPojo`

Converts ONE class from the property-tree style (`LongProperty`, `declareProperty`, ...) to a plain
data class whose msgpack codec is generated. The serialized bytes must not change: state written
by an old broker has to stay readable (rollback, migration), and records are written to the log,
exported and replicated. A frozen copy of the old class (`<Name>V1`) plus a byte comparison test
proves that.

Background: `zeebe/msgpack-codegen/README.md` (read it first, it lists the supported types and the
generator rules).

## Which kind of class is it?

| | A: engine state class | B: record value |
| --- | --- | --- |
| Where | `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/**` | `zeebe/protocol-impl/.../protocol/impl/record/value/**` |
| Old base | `extends UnpackedObject implements DbValue` | `extends UnifiedRecordValue implements <Name>Value` |
| New declaration | `public final class Name extends MsgpackBufferValue<Name> implements DbValue` | `public final class Name extends MsgpackBufferValue<Name> implements MsgpackRecordValue, <Name>Value` |
| `NameV1` goes to | `src/test` of the same module, same package | `src/test` of `zeebe/protocol-impl`, same package (a copy in `src/main` would need a golden: `RecordGoldenFilesTest` demands one for every source under the record package) |
| Gradle project | `:zeebe-workflow-engine` (or the module of the class) | `:zeebe-protocol-impl` |
| Extra proof | bytes | bytes **and** `toJson()` (exporters see the JSON) |
| Golden files | none | `RecordGoldenFilesTest` must pass with the unchanged golden (step 6) |

Worked examples for A, read one first: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/instance/`
`ElementInstance.java`, `IndexedRecord.java`, `TaskListenerIndicesRecord.java`, their `*V1.java`
copies and `ElementInstanceSerdeTest.java` under `src/test`. (They exist once the engine state PR
is in; if they are missing on your branch, ask the user where they live. The workshop examples keep
the `*V1.java` copies in `src/main`; new ones go to `src/test`.)
For B the design is `MsgpackRecordValue`
(`zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/MsgpackRecordValue.java`):
an interface with `wrap(DirectBuffer)`, `toJson()` and `valueType()` as defaults, so a record does
not have to be an `UnpackedObject`. `UnifiedRecordValue` implements it too, so migrated and
unmigrated records mix wherever a `MsgpackRecordValue` is taken.

## Rules

- Migrate exactly one class per run and one commit per class.
- Keep every public/protected method and constructor with the same behavior. Do not rename, remove
  or change signatures (for B also not the getters of the `<Name>Value` interface). Only the
  read/write plumbing may change. If a signature has to change, STOP and ask the user.
- The stored format is: map keys, their order, the default values and the msgpack types. Never
  change them.
- If a step says STOP, stop and report to the user. Do not improvise.
- Kind A: never touch `zb-db` or any `ColumnFamily` code; call sites of the migrated class stay
  unchanged.
- Never edit the generator (`zeebe/msgpack-codegen`) in a migration commit. A missing generator
  feature is a STOP: report it, the user decides whether it goes into the codegen PR first.
- Never run the full repo build. Run only the commands in this file.
- Commits: conventional commit, no scope, every commit has a body (why + what stays identical),
  hard wrap at 72. Author is `Carlo Sana <1188147+entangled90@users.noreply.github.com>`. Never
  add a Claude co-author line.
- Branch: from the branch the user names (the PR stack). Never from `main` unless told.

## Step 0: check the class can be migrated

Open the class and every property it declares.

| Old property                                   | New field                                                                 |
| ---------------------------------------------- | ------------------------------------------------------------------------- |
| `LongProperty(key, default)`                   | `long field = default;`                                                   |
| `IntegerProperty(key, default)`                | `int field = default;`                                                    |
| `BooleanProperty(key, default)`                | `boolean field = default;`                                                |
| `StringProperty(key, default)`                 | `DirectBuffer field = Decoding.EMPTY;` (non-empty default: step 3)        |
| `BinaryProperty(key)`                          | `@MsgpackProperty(binary = true, required = true) DirectBuffer field = Decoding.EMPTY;` |
| `EnumProperty<E>(key)` without default         | `E field;` (null until set, writing null fails like before)               |
| `EnumProperty<E>(key, default)`                | `E field = default;`                                                      |
| `ObjectProperty<X>(key, new X())`              | `final X field = new X();` (X is a `@MsgpackPojo` or a `BaseValue`)       |
| `ArrayProperty<StringValue>`                   | `final List<DirectBuffer> field = new ArrayList<>();`                     |
| `ArrayProperty<IntegerValue>`                  | `final IntArrayList field = new IntArrayList();` (agrona, no boxing)      |
| `ArrayProperty<LongValue>`                     | `final LongArrayList field = new LongArrayList();` (agrona, no boxing)    |

Use `@MsgpackProperty(document = true) DirectBuffer field = DocumentValue.EMPTY_DOCUMENT;` for a
`DocumentProperty`, and `@MsgpackProperty(raw = true) DirectBuffer field = Decoding.EMPTY;` for a
`PackedProperty` (preserve any explicit default). For `ArrayProperty<X>` where X is a `@MsgpackPojo`
or `BaseValue`, use `final List<X> field = new ArrayList<>();`; X needs a public no-arg constructor.

`IntArrayList`/`LongArrayList` store the same msgpack array of integers as the old arrays (and as
`List<Long>`), so the bytes are identical. `IntArrayList` rejects values outside the int range on
read. Use `List<Long>` only when a public getter must keep returning `List<Long>` and converting
inside the getter would be wasteful or impossible. If a getter would have to change its return
type, ask the user first (public API). A property declared with `.sanitized()` is supported: put
`@MsgpackSanitized` on its replacement field to mask it in the generated string representation;
this does not affect the stored bytes. Nested-object lists are supported, but their items are
allocated while reading rather than recycled like `ArrayProperty` items.

Type-use annotations such as `@Nullable` on list elements and nested types are fine, the generator
compares erasures. Nested objects: a nested `@MsgpackPojo` or `BaseValue` is a `final` field.
Do not add `@NullMarked` to the package in the migration commit; the repository asks for
nullness annotations in a separate `refactor:` commit.

STOP and report which one, if the class uses:

- `PropertyGroup`, a computed default, or an unsupported property type;
- an `ArrayProperty` whose item type is not one of the supported types in the table above;
- an inner/nested class or any non-top-level class.

Also check, and STOP if any of these is true:

1. Subclasses or mocks: `grep -rnE "extends Name\b" zeebe --include=*.java` finds nothing, and no
   test mocks or spies it. A class that is merely not `final` is fine, the migrated class is
   `final`; mention it in the commit body.
2. A migrated class is not a `BaseValue`. If another still-unmigrated class holds it in an
   `ObjectProperty<Name>`/`ArrayProperty<Name>`
   (`grep -rnE "(ObjectProperty|ArrayProperty)<Name>" zeebe --include=*.java`), it will not
   compile. Migrate the holder first, or STOP. Kind B: the generic holders (`NestedRecord`,
   `BufferedCommandRecord`, `CommandDistributionRecord`, `PersistedCommandDistribution`) declare
   `ObjectProperty<UnifiedRecordValue>`, so a migrated record cannot be put in them yet: STOP if
   the record is stored there.
3. `UnpackedObject` members the class loses and that something uses: `copyFrom`, `copyTo`,
   `isEmpty`, `getEncodedLength`, `writeJSON`, `toString`, `hashCode`. Search the repo for callers
   on this class (`grep -rn "<method>(" zeebe/ --include=*.java`, check the receiver type).
   Unused: drop. Used: STOP. (`wrap(DirectBuffer)` is provided by `MsgpackRecordValue` for B; for A
   see step 3.6.)
4. Equality and string representation. `MsgpackBufferValue` delegates `equals`, `hashCode` and
   `toString` to the generated `NameOps`; equality and hashing are content-based over stored
   properties, not identity-based. Do not stop just because tests or code rely on value equality.
   Check callers and tests for assumptions about the old behavior: generated equality requires the
   same concrete class, and generated hashing has special handling for nested `BaseValue`s. If
   class-specific or ignored state must participate, stop and report the behavior that needs to be
   preserved rather than claiming value equality is lost.
5. Reading differences: `UnpackedObject` throws when a declared property is missing from the
   input (`verifyAllDeclaredPropertiesAreSet`). Properties with a default never throw there (they
   always have a value), so the generated class keeps the same default: no difference. Properties
   WITHOUT a default keep the throw through `@MsgpackProperty(required = true)` (see below).

Properties WITHOUT a default (`new LongProperty("k")`, `BooleanProperty`, `StringProperty`,
`BinaryProperty`) throw when written before being set and when missing on read. Map them to
`@MsgpackProperty(required = true)` and use the Java default (`0L`, `false`, `Decoding.EMPTY`) as
initializer: `required` keeps the read guard, the initializer gives the field a value to write. The
bytes are identical for everything the old class could write. The new class writes the Java default
where the old one threw on write: in the serde tests set such a value explicitly on both objects,
also in the "defaults" tests (the old class cannot write while unset), and say so in the commit
body. An enum without default is the same: the new class throws `IllegalStateException` instead of
`MsgpackPropertyException` when written unset; mention it in the commit body.

## Step 1: write down the stored layout

From the constructor, list the `declareProperty(...)` calls IN THE ORDER OF THOSE CALLS. This
order, not the order of the field declarations, is the stored order. For each: key string, type,
default. Keys may be constants (`NAME_KEY`) or `new StringValue("..")`; resolve them. You need
this list in steps 3 and 5.

## Step 2: freeze the old class as `<Name>V1`

Copy the current class (and every class you migrate together with it, e.g. nested ones) BEFORE
editing it (do not use `git show`, some environments block it). Same package, renamed, in the TEST
sources of the module: only the serde test needs it, so it must not ship.

```bash
cp <module>/src/main/java/<package path>/Name.java <module>/src/test/java/<package path>/NameV1.java
sed -i -E 's/\b(Name|NestedName)\b/\1V1/g' <module>/src/test/java/<package path>/NameV1.java
```

Replace `Name`/`NestedName` with the real class names (one alternation entry per frozen class). If
the original is `public class`, make the V1 copy `public final class` too. Insert this Javadoc
directly above the class declaration of each V1 file:

```java
/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
```

A V1 copy of a record may drop the `<Name>Value` interface and its `@Override`s if that is
awkward; the comparison only needs `write`, `wrap`, `getLength`, `toJson`, setters and getters.

## Step 3: convert the class

Edit the class in place:

1. Annotate with `@MsgpackPojo` and change the declaration as in the table (drop
   `extends UnpackedObject`/`UnifiedRecordValue`, keep every interface the class had).
   `MsgpackBufferValue` makes it `Recyclable`, which the generator requires: without it
   compilation fails with "must implement io.camunda.zeebe.msgpack.Recyclable". A kind A class that
   did not implement `DbValue`: STOP, not covered. For B, `MsgpackBufferValue` supplies what
   `MsgpackRecordValue` needs (`getLength`, `write`, `wrap(buffer, offset, length)`, `reset`).
2. Replace each `XxxProperty` field by a plain package-private field from the table in step 0.
   Generator rules: fields must NOT be `private`; scalar, string and enum fields must NOT be
   `final` and carry their default as initializer (plus `required = true` if the old property had
   no default, see step 0); nested objects and lists must be `final` and
   initialized inline; initializers must be side-effect free. Declare the fields in the stored
   order from step 1. Field name = key string; if you want a different Java name add
   `@MsgpackProperty("theKey")`. A `final UnsafeBuffer` field is wrapped in place on read and
   allocates nothing; a `DirectBuffer` field gets a new view per read. Use `UnsafeBuffer` only
   when the old code already owned such a buffer, otherwise keep `DirectBuffer`.
3. Add exactly one field
   `@MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();`
   It keeps keys written by newer versions and writes them back (`UnpackedObject` did the same
   with its undeclared properties). A field that must not be stored gets `@MsgpackIgnore`.
4. Delete the `declareProperty` calls and `super(n)`. Keep all constructors; the no-arg
   constructor must remain, reachable from the same package. Constructors do NOT call `reset()`,
   the initializers set the defaults.
5. Add the two required overrides (the compiler fails without them), each returning a static
   instance of the generated classes:
   ```java
   @Override
   protected MsgpackCodec<Name> codec() {
     return NameCodec.INSTANCE;
   }

   @Override
   protected MsgpackOps<Name> ops() {
     return NameOps.INSTANCE;
   }
   ```
   Import `io.camunda.zeebe.msgpack.pojo.MsgpackCodec` and `MsgpackOps`. Do NOT write `reset()`:
   `MsgpackBufferValue` provides it by delegating to the generated `NameOps`, which restores
   initializers, clears lists, resets nested fields and clears `unknownProperties`. Only if the
   class has `@MsgpackIgnore`d state, override `reset()` as
   `{ super.reset(); /* reset ignored state */ }`.
6. Rewrite every method that used a property. Mechanical translations:
   - `prop.getValue()` -> `field`; `prop.setValue(v)` -> `field = v`
   - `prop.increment()` -> `field++`; `prop.decrement()` -> `field--` (if the old code used the
     returned value, keep using it: `final int c = --field;`)
   - `prop.reset()` -> `field = <default>`
   - `StringProperty.setValue(DirectBuffer v)` -> `field = v` (the old code only wrapped the buffer,
     no copy; the field references it as before, never use `BufferUtil.cloneBuffer`)
   - `DocumentProperty.setValue(v)` -> `field = Decoding.document(v)` to retain its normalization
     and validation; use `@MsgpackProperty(document = true)` on the field
   - `PackedProperty` maps to a `@MsgpackProperty(raw = true)` field; preserve its default and keep
     the old no-copy behavior when assigning buffers
   - setting a string to "" -> `field = Decoding.EMPTY`
   - decoding needs no code: strings, binaries, raw values and documents are read as views on the
     input buffer, exactly like the old properties. Never add a copy on read or in a setter to a class
     that had none; a setter keeps a copy only where the legacy setter copied explicitly.
   - `ArrayProperty.add().wrap(buf)` -> `list.add(buf)` (`StringValue.wrap` only creates a view, no
     copy); for
     `IntArrayList`/`LongArrayList` use `addInt(v)` / `addLong(v)`. For nested-object arrays, use a
     `List<X>` as in step 0 and preserve the existing public getter/mutation behavior.
   - methods that returned a stream/list of the array property: keep the return type and return
     a copy or an unmodifiable view as the old code did (check what it did)
   - nested object `prop.getValue()` -> the final nested field
   - Kind A: if the class overrode `wrap(...)` to copy the input bytes first, keep that override
     but make it call `super.wrap(copy, 0, length)` (see `IndexedRecord.wrap`). A class that holds
     an unmigrated `UnpackedObject` as a nested field decodes it by reference to the input buffer,
     so it must copy the input first in the same way. The one-argument `wrap(DirectBuffer)` from
     `UnpackedObject` does not exist on `MsgpackBufferValue`; callers use
     `wrap(buffer, 0, buffer.capacity())` (`grep` for them and rewrite; STOP if outside the engine).
     Test helpers typed on `UnpackedObject` (e.g. `serialize(UnpackedObject)`) no longer accept
     the class: `grep -rn "UnpackedObject" <module>/src/test` and widen the parameter to
     `io.camunda.zeebe.util.buffer.BufferWriter`.
   - Kind B: records were never copied on read (views on the input buffer, like the old
     properties); keep it that way. Jackson serializes records through their public getters
     (`toJson()`, exporters): keep `@JsonIgnore` on every non-record-value getter you keep
     (property-tree helpers, `isEmpty()`). `getLength` is covered, `MsgpackRecordValue`
     redeclares it with `@JsonIgnore`.
7. Codegen dependencies: verify the module has `implementation(project(":zeebe-msgpack-codegen"))`
   and `annotationProcessor(project(":zeebe-msgpack-codegen"))` in its `build.gradle.kts`, plus a
   `zeebe-msgpack-codegen` dependency and its annotation-processor path in `pom.xml`. The engine is
   not wired for this yet; do not assume either build is already configured. Add the wiring to both
   build systems in the first migrated class's commit and mention it in the commit body. The
   `microbenchmarks` Gradle and Maven files show the current configuration pattern.

## Step 4: compile and inspect the generated codec

```bash
LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :<project>:compileJava -q 2>&1 | grep -E "error:|What went wrong" -A5
```

Project path: see the table, or look it up in `settings.gradle.kts`. Fix compile errors in your
class, not in the generator; the processor message tells you which rule was broken. Then compile
the modules that use the class (no signature changed, so this must be a no-op; a lost method shows
up here). For B:

```bash
./gradlew :zeebe-workflow-engine:compileTestJava :zeebe-broker:compileTestJava \
  :zeebe-stream-platform:compileTestJava :zeebe-protocol-test-util:compileJava -q
```

The generated code is in
`<module>/build/generated/sources/annotationProcessor/java/main/<package path>/NameCodec.java`;
check that its `KEY_n` constants appear in the stored order from step 1. The generated `NameOps.java`
is next to it. Inspect its `reset`, `equals`, `hashCode` and `toString` operations as needed.

## Step 5: write the byte comparison test

Copy `serde-test-template.md` (next to this file) to `<Name>SerdeTest.java` in the test sources of
the same module and package, and fill it in. It must contain, for the new class vs `NameV1`:

1. same bytes when every property is set to a non-default value,
2. same bytes with only the constructor-required values set (defaults everywhere else),
3. reads the bytes written by `NameV1` into a target whose EVERY property was first set to a
   non-default value (proves the generated reset restores every default),
4. is read by `NameV1`,
5. unknown properties of a newer version survive read, modify, write,
6. kind B only: `toJson()` equals that of `NameV1` for a fully populated value,
7. a decoded value does not reference the buffer it was read from: only for classes that copy the
   input in `wrap`, or whose legacy setters copied explicitly. Skip it for classes that only had
   views before.

Every property from step 1 must be set to a non-default value in test 1. Use a checklist.

## Step 6: verify

```bash
LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :<project>:test --tests '<package>.NameSerdeTest' -q
LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :<project>:test --tests '<package of the class>.*' -q
```

Then the tests of everything that uses the class (`grep -rln "Name" <module>/src/test`). Kind A:
also `--tests 'io.camunda.zeebe.engine.state.*'`. Kind B: the whole `:zeebe-protocol-impl:test`, and
the engine processors and exporter tests that touch the record. Run Gradle with
`LC_ALL=C.UTF-8 LANG=C.UTF-8`, otherwise reports of tests with non-ASCII names fail with "Malformed
input" and the XML files stay stale. `-q` prints nothing on success; to confirm tests ran, sum the
XML files (nested test classes get mangled file names):
`grep -ho 'tests="[0-9]*"' <module>/build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ | bc`.
A failure means behavior changed: fix the class (usually a missing copy, a wrong default or a wrong
order), never the test and never the V1 copy.

### Kind B: `RecordGoldenFilesTest`

The goldens (`zeebe/protocol-impl/src/test/resources/protocol/records/golden/Name.golden`) are
verbatim copies of the old source, and the test compares the keys and types of every record with
them. It reads the fields of a `@MsgpackPojo` (key = field name or `@MsgpackProperty` value, type =
the property type the field replaces) and finds a renamed field by its key.

- Do NOT refresh the golden of a migrated record. The old source is the independent proof that no
  key or type changed; refreshing it would make the test compare the new code with itself.
- The test must pass with the old golden. If it fails with "Expected property ... from golden file
  to exist in source" or "type changed", the migration changed the stored format: fix the class.
  If the message is about a shape the test does not understand (e.g. a type it cannot map), STOP
  and report it; do not edit the golden or the test in the migration commit.
- A property added later to a migrated record fails the test as for any record; refresh the golden
  then, with the new source, as usual.

## Step 7: format and commit

```bash
./gradlew :<project>:spotlessApply
```

Then `git add` only the files you changed (the class, `NameV1`, `NameSerdeTest`, build files for the
first class of a module, nothing under the goldens), commit and push. Header example:
`refactor: replace UnpackedObject based Foo with a generated codec`. The body must say that the
stored format is unchanged and that the V1 comparison tests prove it, and list actual behavior
changes (for example, final class or unset-property behavior). Generated `NameOps` provides
content-based equality, hashing and string output. Gradle may regenerate
`dist/src/main/config/defaults.yaml`; revert it with `git checkout`.

## When to STOP and report

- Unsupported property type, subclass, nested class, or a holder that is not migrated (step 0).
- A lost `UnpackedObject` member is used, or step 0 identifies an equality/string behavior mismatch
  that the migration cannot preserve as written.
- Kind B: `RecordGoldenFilesTest` fails and the cause is not a changed key or type (step 6).
- Any V1 comparison fails and the cause is not obvious after one fix attempt.
- The class is used by code outside its module in a way that needs a signature change.
- You are tempted to edit the generator, `zb-db`, a `ColumnFamily`, the V1 copy, or a golden to make
  something pass.
