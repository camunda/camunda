/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.compiled;
import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.complete;
import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.defaultValue;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.actual;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.comparable;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.declaredOrderMessage;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.entriesOf;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.populate;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.randomOrderMessage;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.unknownKey;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.wrap;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.write;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Compiled;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.FieldSpec;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.NestedValue;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Scenario;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Type;
import java.util.ArrayList;
import java.util.Arrays;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Generates {@link RandomShapes} (any mix of the supported field types and custom keys), compiles
 * them with the processor and checks the generated codec against the independent {@link
 * ReferenceModel} of the msgpack format.
 *
 * <p>Shapes whose lists start empty are also checked against the equivalent {@link UnpackedTwin},
 * the implementation the codec replaces.
 *
 * <p>Messages are written in random key order, with random keys missing and random unknown entries
 * in between, at a random offset of a larger buffer.
 */
@Tag("randomized")
final class GeneratedCodecPropertyTest {

  /** Compiling a shape takes longer than checking it, so both checks share the compiled class. */
  @Property(tries = 40)
  void shouldReadAndWriteTheMsgpackFormat(@ForAll("scenarios") final Scenario scenario)
      throws Exception {
    assertReadsAnyKeyOrderMissingKeysAndUnknownKeys(scenario);
    assertWritesTheMsgpackFormatAndReadsItBack(scenario);
    assertCopiesIntoAnIndependentInstance(scenario);
    if (scenario.schema().emptyLists()) {
      assertMatchesAnUnpackedObject(scenario);
    }
  }

  /** A copy stores the same bytes, whatever the target held, and survives reuse of the source. */
  private static void assertCopiesIntoAnIndependentInstance(final Scenario scenario)
      throws Exception {
    // given a source read from a message, and a target that holds another value
    final var compiled = compiled(scenario.schema());
    final var source = compiled.newInstance();
    final var message = randomOrderMessage(scenario, false);
    final var sourceBuffer = new UnsafeBuffer(new byte[scenario.offset() + message.length + 8]);
    sourceBuffer.putBytes(scenario.offset(), message);
    ((MsgpackBufferValue<?>) source).wrap(sourceBuffer, scenario.offset(), message.length);
    final var expected = new ExpandableArrayBuffer();
    final int expectedLength = write(source, expected, scenario.offset());
    final var target = compiled.newInstance();
    wrap(
        target,
        declaredOrderMessage(scenario.schema(), scenario.previousValues()),
        scenario.offset());

    // when it is copied, and the buffer it was read from is reused
    @SuppressWarnings({"unchecked", "rawtypes"})
    final MsgpackBufferValue<?> copying = (MsgpackBufferValue) target;
    copyFrom(copying, source);
    assertThat(target)
        .as("a copy equals its source%n%s", scenario.schema().source())
        .isEqualTo(source)
        .hasSameHashCodeAs(source);
    sourceBuffer.setMemory(0, sourceBuffer.capacity(), (byte) 0xc1);

    // then
    final var actual = new ExpandableArrayBuffer();
    final int actualLength = write(target, actual, scenario.offset());
    assertThat(new UnsafeBuffer(actual, scenario.offset(), actualLength))
        .as("copy%n%s", scenario.schema().source())
        .isEqualTo(new UnsafeBuffer(expected, scenario.offset(), expectedLength));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void copyFrom(final MsgpackBufferValue target, final Object source) {
    target.copyFrom((MsgpackBufferValue) source);
  }

  private static void assertReadsAnyKeyOrderMissingKeysAndUnknownKeys(final Scenario scenario)
      throws Exception {
    // given an instance that holds another value, as a reused instance does
    final var compiled = compiled(scenario.schema());
    final var instance = compiled.newInstance();
    final var previous = declaredOrderMessage(scenario.schema(), scenario.previousValues());
    wrap(instance, previous, scenario.offset());

    // and a message in random key order, without some keys and with unknown keys in between
    final var message = randomOrderMessage(scenario, false);

    // when
    wrap(instance, message, scenario.offset());

    // then present fields are read and missing ones are back to their default
    for (final var field : scenario.schema().fields()) {
      final var value =
          scenario.present().get(field.index()) ? scenario.values().get(field.index()) : null;
      assertField(scenario, compiled, instance, field, value);
    }

    // and the unknown entries are written back untouched
    final var written = new ExpandableArrayBuffer();
    final int length = write(instance, written, scenario.offset());
    final var writtenEntries = entriesOf(written, scenario.offset(), length);
    final int expectedUnknown = scenario.unknowns().size();
    assertThat(writtenEntries)
        .as(
            "every declared key and the unknown ones are written once%n%s",
            scenario.schema().source())
        .hasSize(scenario.schema().fields().size() + expectedUnknown);
    for (int i = 0; i < scenario.unknowns().size(); i++) {
      final var unknown = scenario.unknowns().get(i);
      assertThat(writtenEntries.get(unknownKey(i, unknown)))
          .as("unknown entry %d%n%s", i, scenario.schema().source())
          .isEqualTo(unknown.value());
    }
    for (final var field : scenario.schema().fields()) {
      if (field.type() == Type.NESTED && scenario.present().get(field.index())) {
        final var nested = (NestedValue) scenario.values().get(field.index());
        assertThat(writtenEntries)
            .as("the nested value is written under its key%n%s", scenario.schema().source())
            .containsKey(field.key());
        final var innerEntries =
            entriesOf(
                new UnsafeBuffer(writtenEntries.get(field.key())),
                0,
                writtenEntries.get(field.key()).length);
        assertThat(innerEntries.containsKey("nu"))
            .as("the unknown key of the nested value survives")
            .isEqualTo(nested.withUnknown());
      }
    }
  }

  /**
   * An UnpackedObject with the same keys, types and defaults is the implementation the generated
   * codec replaces, so it is the reference for what is stored: the bytes both write must be the
   * same, and each must read what the other wrote. Both keep unknown keys and write them after the
   * declared ones.
   */
  private static void assertMatchesAnUnpackedObject(final Scenario scenario) throws Exception {
    final var schema = scenario.schema();
    final var compiled = compiled(schema);

    // given both read a message in random key order with unknown keys
    final var message = randomOrderMessage(scenario, true);
    final var generated = compiled.newInstance();
    wrap(generated, message, scenario.offset());
    final var twin = new UnpackedTwin(schema);
    twin.wrap(new UnsafeBuffer(message), 0, message.length);

    // then they hold the same fields
    for (final var field : schema.fields()) {
      assertThat(twin.actual(field))
          .as("%s (%s) read by the UnpackedObject%n%s", field.name(), field.type(), schema.source())
          .isEqualTo(actual(compiled, generated, field));
    }

    // and write the same bytes, which also holds for what they write after being read back
    final var generatedOut = new ExpandableArrayBuffer();
    final int generatedLength = write(generated, generatedOut, scenario.offset());
    final var generatedBytes =
        Arrays.copyOfRange(
            generatedOut.byteArray(), scenario.offset(), scenario.offset() + generatedLength);
    final var twinBytes = new byte[twin.getLength()];
    twin.write(new UnsafeBuffer(twinBytes), 0);
    assertThat(generatedBytes).as(schema.source()).isEqualTo(twinBytes);
    // and print the same JSON, which logs and assertion messages show
    assertThat(generated.toString()).as(schema.source()).isEqualTo(twin.toString());

    // and each reads what the other wrote, as a rolled back version does
    final var readTwinBytes = compiled.newInstance();
    wrap(readTwinBytes, twinBytes, scenario.offset());
    final var readGeneratedBytes = new UnpackedTwin(schema);
    readGeneratedBytes.wrap(new UnsafeBuffer(generatedBytes), 0, generatedBytes.length);
    for (final var field : schema.fields()) {
      assertThat(actual(compiled, readTwinBytes, field))
          .as(
              "%s (%s) read from bytes of the UnpackedObject%n%s",
              field.name(), field.type(), schema.source())
          .isEqualTo(twin.actual(field));
      assertThat(readGeneratedBytes.actual(field))
          .as(
              "%s (%s) of the UnpackedObject read from bytes of the codec%n%s",
              field.name(), field.type(), schema.source())
          .isEqualTo(actual(compiled, generated, field));
    }
  }

  private static void assertWritesTheMsgpackFormatAndReadsItBack(final Scenario scenario)
      throws Exception {
    // given an instance with every field set
    final var compiled = compiled(scenario.schema());
    final var instance = compiled.newInstance();
    final var values = new ArrayList<Object>();
    for (final var field : scenario.schema().fields()) {
      values.add(complete(field, scenario.values().get(field.index())));
    }
    populate(compiled, instance, scenario.schema(), values);
    final var expected = declaredOrderMessage(scenario.schema(), values);

    // when
    final var out = new ExpandableArrayBuffer();
    final int length = write(instance, out, scenario.offset());

    // then the bytes are the map of all keys in declaration order, as the length says
    assertThat(((MsgpackBufferValue<?>) instance).getLength()).isEqualTo(expected.length);
    assertThat(length).isEqualTo(expected.length);
    assertThat(Arrays.copyOfRange(out.byteArray(), scenario.offset(), scenario.offset() + length))
        .as(scenario.schema().source())
        .isEqualTo(expected);

    // and reading them back gives the same fields, and writing those gives the same bytes
    final var read = compiled.newInstance();
    wrap(read, expected, scenario.offset());
    for (final var field : scenario.schema().fields()) {
      assertField(scenario, compiled, read, field, values.get(field.index()));
    }
    final var again = new ExpandableArrayBuffer();
    final int againLength = write(read, again, 0);
    assertThat(Arrays.copyOf(again.byteArray(), againLength)).isEqualTo(expected);
  }

  private static void assertField(
      final Scenario scenario,
      final Compiled compiled,
      final Object instance,
      final FieldSpec field,
      final Object value)
      throws ReflectiveOperationException {
    final var expected =
        comparable(
            field.type(),
            value == null ? defaultValue(field.type(), scenario.schema().emptyLists()) : value);
    assertThat(actual(compiled, instance, field))
        .as(
            "%s (%s, %s)%n%s",
            field.name(),
            field.type(),
            value == null ? "missing in the message" : "present",
            scenario.schema().source())
        .isEqualTo(expected);
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    return RandomShapes.scenarios();
  }
}
