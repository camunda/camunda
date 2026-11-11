---
name: migrate-state-to-msgpack-pojo
description: Use when converting a state class that extends UnpackedObject (and usually implements DbValue) into a plain @MsgpackPojo data class with a generated codec, keeping the stored bytes identical. One class per run.
---

# Migrate a state class to a `@MsgpackPojo`

Converts ONE class from the property-tree style (`extends UnpackedObject`, `LongProperty`, ...)
to a plain data class whose msgpack codec is generated. The stored bytes must not change, because
state written by an old broker has to stay readable (rollback, migration). A frozen copy of the
old class plus a byte comparison test proves that.

Worked example, read it first: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/instance/`
`ElementInstance.java`, `IndexedRecord.java`, `TaskListenerIndicesRecord.java`, their `*V1.java`
copies and `ElementInstanceSerdeTest.java` under `src/test`. Background: `zeebe/msgpack-codegen/README.md`.

## Rules

- Migrate exactly one class per run and one commit per class.
- Keep every public/protected method and constructor, with the same behavior. Do not rename,
  remove or change signatures. Only the read/write plumbing may change.
- The stored format is: map keys, their order, and the default values. Never change them.
- If a step says STOP, stop and report to the user. Do not improvise.
- Never touch `zb-db` or any `ColumnFamily` code. Call sites of the migrated class stay unchanged.
- Never run the full repo build. Run only the commands in this file.
- Commits: conventional commit, no scope, body explaining why, hard wrap at 72. Author is
  `Carlo Sana <1188147+entangled90@users.noreply.github.com>`, never add a Claude co-author line.

Base branch: the skill and the finished examples live on `claude/lucid-mendel-oaaxtq`. Branch from
it, not from `main`.

## Step 0: check the class can be migrated

Open the class and every property it declares. Allowed property types:

| Old property                                  | New field                                                        |
| --------------------------------------------- | ---------------------------------------------------------------- |
| `LongProperty(key, default)`                  | `long field = default;`                                          |
| `IntegerProperty(key, default)`               | `int field = default;`                                           |
| `BooleanProperty(key, default)`               | `boolean field = default;`                                       |
| `StringProperty(key, default)`                | `DirectBuffer field = Decoding.EMPTY;` (non-empty default: see step 3) |
| `BinaryProperty(key)`                         | `@MsgpackProperty(binary = true) DirectBuffer field = Decoding.EMPTY;` |
| `EnumProperty<E>(key)` without default        | `E field;` (null until set, writing null fails like before)      |
| `EnumProperty<E>(key, default)`               | `E field = default;`                                             |
| `ObjectProperty<X>(key, new X())`             | `final X field = new X();` (X is a `@MsgpackPojo` or an `UnpackedObject`) |
| `ArrayProperty<StringValue>(key, StringValue::new)` | `final List<DirectBuffer> field = new ArrayList<>();`      |
| `ArrayProperty<LongValue>(key, LongValue::new)` | `final List<Long> field = new ArrayList<>();`                  |

STOP if the class uses `DocumentProperty`, `PackedProperty`, `ArrayProperty` of
anything but `StringValue` or `LongValue`, `PropertyGroup`, a property with `.sanitized()`, or any property
whose default is computed. Report which one. The generator does not support them yet.

Also STOP if the class is a nested/inner class (not supported), or if anything extends it:
`grep -rnE "extends Name\b" zeebe --include=*.java` must find nothing, and no test may mock or
spy it. A class that is merely not `final` is fine: the migrated class is declared `final`, and
you mention that in the commit body.

Properties WITHOUT a default (`new BooleanProperty("k")`, `LongProperty`, `StringProperty`,
`BinaryProperty` without a default argument) throw when
written before being set. Use the Java default (`false`, `0L`) as the initializer; the stored
bytes are identical for everything the old class could write. In the serde tests set such a
value explicitly on both the old and the new object, also in the "defaults" tests (the old class
cannot write while unset, so those tests set it on both sides), and say so in the commit body.
An enum without default is the same: the new class throws `IllegalStateException` instead of
`MsgpackPropertyException` when written unset; mention it in the commit body.

## Step 1: write down the stored layout

From the constructor, list `declareProperty(...)` calls IN THE ORDER OF THOSE CALLS. This order,
not the order of the field declarations, is the stored order. For each: key string, type, default.
Keep this list, you need it in steps 3 and 6. The defaults you write down become the field initializers.

## Step 2: freeze the old class as `<Name>V1`

Copy the current class (and every other class you are migrating together with it, e.g. nested
ones that are state classes) into the test sources, same package, renamed:

```bash
cp <path to Name.java> <module>/src/test/java/<same package path>/NameV1.java
sed -i -E 's/\b(Name|NestedName)\b/\1V1/g' <module>/src/test/java/<same package path>/NameV1.java
```

Do this BEFORE you edit the class (do not use `git show`, some environments block it).

Replace `Name`/`NestedName` with the real class names (one alternation entry per frozen class).
Insert this Javadoc directly above the class declaration in each V1 file (if the original is
`public class`, make the V1 copy `public final class` too):

```java
/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
```

## Step 3: convert the class

Edit the class in place:

1. Annotate with `@MsgpackPojo`. Change the declaration to
   `public final class Name extends MsgpackBufferValue<Name> implements DbValue` (drop
   `extends UnpackedObject`; keep `implements DbValue` if it had it; if it did not implement
   `DbValue`, drop `MsgpackBufferValue` too and STOP-report, this skill does not cover it).
   `MsgpackBufferValue` makes the class `Recyclable`, which the generator requires: without it
   compilation fails with "must implement io.camunda.zeebe.msgpack.Recyclable".
2. Replace each `XxxProperty` field by a plain package-private field from the table in step 0.
   Rules the generator enforces: fields must NOT be `private`; scalar, string, enum fields must
   NOT be `final` and carry their default as initializer; nested objects and lists MUST be
   `final` and initialized inline. Initializers must be side-effect free. Declare the fields in the stored order from
   step 1. Field name = key string. If the key differs from the Java name you want, keep the key as
   field name anyway, or add `@MsgpackProperty("theKey")` on the field.
3. Add exactly one field `@MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();`
   (it keeps keys written by newer versions). A field that must not be stored gets `@MsgpackIgnore`.
4. Delete the `declareProperty` constructor body and `super(n)`. Keep all constructors; the no-arg
   constructor must remain, reachable from the same package. Constructors do NOT call `reset()`,
   the initializers already set the defaults.
5. Do NOT write `reset()` or a `codec()` method. `MsgpackBufferValue` provides `reset()` (it calls
   the generated `NameResetter`, which restores the field initializers, clears lists, resets nested
   objects and clears `unknownProperties`) and finds the generated `NameCodec` by itself. Only if
   the class has state marked `@MsgpackIgnore`, override `reset()` as
   `{ super.reset(); /* reset the ignored state */ }`.
6. Rewrite every method that used a property. Mechanical translations:
   - `prop.getValue()` -> `field`; `prop.setValue(v)` -> `field = v`
   - `prop.increment()` -> `field++`; `prop.decrement()` -> `field--` (if the old code used the
     returned value, keep using it: `final int c = --field;`)
   - `prop.reset()` -> `field = <default>`
   - `StringProperty.setValue(DirectBuffer v)` -> `field = BufferUtil.cloneBuffer(v)` (the old
     code copied into its own storage; the caller may reuse its buffer)
   - setting a string to "" -> `field = Decoding.EMPTY`
   - `ArrayProperty.add().wrap(buf)` -> `list.add(BufferUtil.cloneBuffer(buf))`
   - methods that returned a stream/list of the array property -> return `List.copyOf(list)`
   - nested object `prop.getValue()` -> the final nested field
   - If the class overrode `wrap(...)` to copy the input bytes first, keep that override but make
     it call `super.wrap(copy, 0, length)`. See `IndexedRecord.wrap`.
   - Drop `UnpackedObject`-only methods you do not find used anywhere (`toString`, `hashCode`,
     `writeJSON`, `copyFrom`). Search the repo first: `grep -rn "<method>" zeebe/ --include=*.java`.
     If something uses them, STOP-report.
   - `UnpackedObject` also gave content-based `equals`/`hashCode`. If the old class did not
     override them, the new one loses value equality (it uses identity). Search for equality use
     (`assertThat(x).isEqualTo(`, `equals(`, sets or map keys of the class). If any exists, STOP-
     report; if none, say in the commit body that value equality is gone.
   - The one-argument `wrap(DirectBuffer)` inherited from `UnpackedObject` does not exist on
     `MsgpackBufferValue`; callers use `wrap(buffer, 0, buffer.capacity())`. `grep` for callers of the
     one-argument form on the class and rewrite them; if the call is outside the engine, STOP-report.
   - Test helpers typed on `UnpackedObject` (for example `serialize(UnpackedObject)`) no longer
     accept the class. `grep -rn "UnpackedObject" <module>/src/test` and widen such parameters to
     `io.camunda.zeebe.util.buffer.BufferWriter`.
7. Dependencies: the module must have `implementation(project(":zeebe-msgpack-codegen"))` and
   `annotationProcessor(project(":zeebe-msgpack-codegen"))` in its `build.gradle.kts`, and in its
   `pom.xml` the `zeebe-msgpack-codegen` dependency plus the `annotationProcessorPaths` block copied
   from `zeebe/engine/pom.xml`. `zeebe/engine` already has them. Any other module: add them, and
   say so in the commit message.

If the class contains an `UnpackedObject` that is not itself migrated (like `ProcessInstanceRecord`
in `IndexedRecord`), it is nested as a normal `final` field and decoded by reference to the input
buffer. Therefore the class must override `wrap` to copy the input first (see `IndexedRecord`).

## Step 4: compile and inspect the generated codec

```bash
./gradlew :<project>:compileJava -q 2>&1 | grep -E "error:|What went wrong" -A5
```

Project path: look it up in `settings.gradle.kts` (`zeebe/engine` is `:zeebe-workflow-engine`).
Fix compile errors in your class, not in the generator. A processor error message tells you which
rule was broken. The generated code is in
`<module>/build/generated/sources/annotationProcessor/java/main/<package path>/NameCodec.java`;
check that its `KEY_n` constants appear in the stored order from step 1. The resetter is next
to it, as `NameResetter.java`.

## Step 5: write the byte comparison test

Copy `serde-test-template.md` (next to this file) into `<Name>SerdeTest.java` in the test sources
of the same package and fill it in. The test must contain, for the new class vs `NameV1`:

1. same bytes when every property is set to a non-default value,
2. same bytes with only the constructor-required values set (defaults everywhere else),
3. reads the bytes written by `NameV1` into a target in which EVERY property was first set to a
   non-default value (proves the generated reset restores every default),
4. is read by `NameV1`,
5. unknown properties of a newer version survive read, modify, write,
6. a decoded value does not reference the buffer it was read from (only if it has string/array
   properties).

Every property from step 1 must be set to a non-default value in test 1. Use a checklist.

## Step 6: verify

```bash
./gradlew :<project>:test --tests '<package>.NameSerdeTest' -q
./gradlew :<project>:test --tests '<package of the class>.*' -q
```

Then run the tests of everything that uses the class (search with
`grep -rln "Name" <module>/src/test`), and for the engine the state tests:
`--tests 'io.camunda.zeebe.engine.state.*'`. All must pass. Run Gradle with `LC_ALL=C.UTF-8 LANG=C.UTF-8`, otherwise reports of tests with non-ASCII names
fail with "Malformed input" and the XML files stay stale. Gradle `-q` prints nothing on success. To confirm tests ran, sum the XML files, because nested
test classes get mangled file names:
`grep -ho 'tests="[0-9]*"' <module>/build/test-results/test/*.xml | grep -o '[0-9]*' | paste -sd+ | bc`.
A failure means behavior changed:
fix the class (usually a missing copy, a wrong default, or a wrong order), never the test, and
never the V1 copy.

To confirm tests ran: the XML in `<module>/build/test-results/test/` has `tests="N"` with N > 0.

## Step 7: format and commit

```bash
./gradlew :<project>:spotlessApply
```

Then:

```bash
git add <module> settings.gradle.kts   # only the files you changed
git commit            # type: perf, header: name the effect, body: why + what stays identical
git push
```

Commit header example: `refactor: replace UnpackedObject based Foo with a generated codec`. The body
must say that the stored format is unchanged and that the V1 comparison tests prove it.

## When to STOP and report

- Unsupported property type, subclass, or non-top-level class (step 0).
- Any V1 comparison test fails and the cause is not obvious after one fix attempt.
- The class is used by code outside its module in a way that needs a signature change.
- You are tempted to edit the generator, `zb-db`, a `ColumnFamily`, or the V1 copy.
