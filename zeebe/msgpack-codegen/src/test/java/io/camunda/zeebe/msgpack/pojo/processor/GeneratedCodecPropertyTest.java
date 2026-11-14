/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.compile;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BaseProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.PackedProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.Tuple;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Generates random POJO shapes (any mix of the supported field types and custom keys), compiles
 * them with the processor and checks the generated codec against an independent model of the
 * msgpack format.
 *
 * <p>Shapes whose lists start empty are also checked against an equivalent {@link UnpackedObject},
 * the implementation the codec replaces.
 *
 * <p>The model writes the bytes with the {@link MsgPackWriter} and reads fields back with
 * reflection, so it shares no code with the generator. Messages are written in random key order,
 * with random keys missing and random unknown entries in between, at a random offset of a larger
 * buffer.
 */
@Tag("randomized")
final class GeneratedCodecPropertyTest {

  private static final String IMPORTS =
      """
      package sample;
      import io.camunda.zeebe.msgpack.pojo.*;
      import java.util.*;
      import org.agrona.DirectBuffer;
      import org.agrona.concurrent.UnsafeBuffer;
      """;

  private static final String[] KIND_NAMES = {"ONE", "TWO", "THREE"};
  private static final int DEFAULT_KIND = 1;
  private static final int DEFAULT_NESTED_VALUE = 3;
  private static final int[] LENGTHS = {0, 1, 5, 31, 32, 33, 255, 256};
  private static final List<Long> BOUNDARIES =
      List.of(
          -33L,
          -32L,
          -1L,
          0L,
          127L,
          128L,
          255L,
          256L,
          65_535L,
          65_536L,
          -128L,
          -129L,
          -32_768L,
          -32_769L,
          (long) Integer.MAX_VALUE,
          (long) Integer.MIN_VALUE,
          (long) Integer.MAX_VALUE + 1,
          (long) Integer.MIN_VALUE - 1,
          4_294_967_295L,
          4_294_967_296L);

  private static final Map<String, Compiled> COMPILED = new ConcurrentHashMap<>();

  // ------------------------------------------------------------------ properties

  /** Compiling a shape takes longer than checking it, so both checks share the compiled class. */
  @Property(tries = 40)
  void shouldReadAndWriteTheMsgpackFormat(@ForAll("scenarios") final Scenario scenario)
      throws Exception {
    assertReadsAnyKeyOrderMissingKeysAndUnknownKeys(scenario);
    assertWritesTheMsgpackFormatAndReadsItBack(scenario);
    if (scenario.schema().emptyLists()) {
      assertMatchesAnUnpackedObject(scenario);
    }
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
   * Keys in random order, some of them missing, with unknown entries in between.
   *
   * @param nestedRequired write the nested values even when the scenario leaves them out, because
   *     an UnpackedObject has no default for an object property and rejects a message without it
   */
  private static byte[] randomOrderMessage(final Scenario scenario, final boolean nestedRequired) {
    final var random = new Random(scenario.orderSeed());
    final var entries = new ArrayList<Entry>();
    for (final var field : scenario.schema().fields()) {
      final boolean present =
          scenario.present().get(field.index()) || (nestedRequired && field.type() == Type.NESTED);
      if (present) {
        final var value = scenario.values().get(field.index());
        entries.add(entry(field.key(), encodeValue(field, value, random, false)));
      }
    }
    for (int i = 0; i < scenario.unknowns().size(); i++) {
      entries.add(
          entry(unknownKey(i, scenario.unknowns().get(i)), scenario.unknowns().get(i).value()));
    }
    Collections.shuffle(entries, random);
    return message(entries);
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
    final var twin = new Twin(schema);
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

    // and each reads what the other wrote, as a rolled back version does
    final var readTwinBytes = compiled.newInstance();
    wrap(readTwinBytes, twinBytes, scenario.offset());
    final var readGeneratedBytes = new Twin(schema);
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

  // ------------------------------------------------------------------ arbitraries

  @Provide
  Arbitrary<Scenario> scenarios() {
    return schemas()
        .flatMap(
            schema ->
                Combinators.combine(
                        valuesOf(schema),
                        valuesOf(schema),
                        Arbitraries.frequency(Tuple.of(3, true), Tuple.of(1, false))
                            .list()
                            .ofSize(schema.fields().size()),
                        Arbitraries.longs(),
                        unknownEntries(),
                        Arbitraries.integers().between(0, 16))
                    .as(
                        (values, previous, present, seed, unknowns, offset) ->
                            new Scenario(
                                schema, values, previous, present, seed, unknowns, offset)));
  }

  private static Arbitrary<Schema> schemas() {
    return Arbitraries.of(Type.values())
        .list()
        .ofMinSize(1)
        .ofMaxSize(12)
        .flatMap(
            types -> {
              final var fields = new ArrayList<Arbitrary<FieldSpec>>();
              for (int i = 0; i < types.size(); i++) {
                final int index = i;
                final var type = types.get(i);
                fields.add(
                    Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars('-', '_', 'é', '"', '\\')
                        .ofMaxLength(40)
                        .injectNull(0.5)
                        .map(suffix -> new FieldSpec(index, type, suffix)));
              }
              return Combinators.combine(fields)
                  .as(list -> list)
                  .flatMap(
                      list -> Arbitraries.of(true, false).map(empty -> new Schema(list, empty)));
            });
  }

  private static Arbitrary<List<Object>> valuesOf(final Schema schema) {
    final var perField = new ArrayList<Arbitrary<Object>>();
    for (final var field : schema.fields()) {
      perField.add(valueOf(field.type()));
    }
    return Combinators.combine(perField).as(list -> list);
  }

  private static Arbitrary<Object> valueOf(final Type type) {
    return switch (type) {
      case LONG -> longs().map(v -> (Object) v);
      case INT -> ints().map(v -> (Object) v);
      case BOOLEAN -> Arbitraries.of(true, false).map(v -> (Object) v);
      case STRING -> stringBytes(lengths()).map(v -> (Object) v);
      case BINARY -> binaryBytes(lengths()).map(v -> (Object) v);
      case RAW ->
          Arbitraries.longs().map(GeneratedCodecPropertyTest::randomValue).map(v -> (Object) v);
      case ENUM -> Arbitraries.integers().between(0, KIND_NAMES.length - 1).map(v -> (Object) v);
      case STRING_LIST ->
          stringBytes(Arbitraries.integers().between(0, 40))
              .list()
              .ofMaxSize(20)
              .map(v -> (Object) v);
      case LONG_LIST -> longs().list().ofMaxSize(20).map(v -> (Object) v);
      case NESTED ->
          Combinators.combine(
                  Arbitraries.integers().injectNull(0.3),
                  stringBytes(Arbitraries.integers().between(0, 40)).injectNull(0.3),
                  Arbitraries.of(true, false))
              .as(NestedValue::new)
              .map(v -> (Object) v);
    };
  }

  private static Arbitrary<List<UnknownEntry>> unknownEntries() {
    return Combinators.combine(
            Arbitraries.strings().alpha().ofMaxLength(35),
            Arbitraries.longs().map(GeneratedCodecPropertyTest::randomValue))
        .as(UnknownEntry::new)
        .list()
        .ofMaxSize(4);
  }

  private static Arbitrary<Integer> lengths() {
    return Arbitraries.oneOf(
        Arbitraries.of(0, 1, 31, 32, 255, 256, 300), Arbitraries.integers().between(0, 300));
  }

  private static Arbitrary<Long> longs() {
    return Arbitraries.oneOf(
        Arbitraries.longs(), Arbitraries.longs().between(-40, 70_000), Arbitraries.of(BOUNDARIES));
  }

  private static Arbitrary<Integer> ints() {
    return Arbitraries.oneOf(
        Arbitraries.integers(),
        Arbitraries.integers().between(-40, 70_000),
        Arbitraries.of(BOUNDARIES)
            .filter(v -> v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)
            .map(Long::intValue));
  }

  private static Arbitrary<byte[]> stringBytes(final Arbitrary<Integer> lengths) {
    return lengths.flatMap(
        n ->
            Arbitraries.strings()
                .ascii()
                .withChars('é', '€')
                .ofLength(n)
                .map(s -> s.getBytes(StandardCharsets.UTF_8)));
  }

  private static Arbitrary<byte[]> binaryBytes(final Arbitrary<Integer> lengths) {
    return lengths.flatMap(n -> Arbitraries.bytes().array(byte[].class).ofSize(n));
  }

  // ------------------------------------------------------------------ the model

  private static Entry entry(final String key, final byte[] value) {
    return new Entry(key.getBytes(StandardCharsets.UTF_8), value);
  }

  private static String unknownKey(final int index, final UnknownEntry unknown) {
    return "u" + index + unknown.suffix();
  }

  /** What a field holds when its key is missing: the initializer in {@link FieldSpec}. */
  private static Object defaultValue(final Type type, final boolean emptyLists) {
    return switch (type) {
      case LONG -> -7L;
      case INT -> 5;
      case BOOLEAN -> true;
      case STRING, BINARY -> new byte[] {'d', 1};
      case RAW -> new byte[] {(byte) 0xc0};
      case ENUM -> DEFAULT_KIND;
      case STRING_LIST -> emptyLists ? List.of() : List.of(new byte[] {'x'});
      case LONG_LIST -> emptyLists ? List.of() : List.of(9L);
      case NESTED -> new NestedValue(null, null, false);
    };
  }

  /** The value with its missing nested parts replaced by their defaults. */
  private static Object complete(final FieldSpec field, final Object value) {
    if (field.type() == Type.NESTED) {
      final var nested = (NestedValue) value;
      return new NestedValue(
          nested.value() == null ? DEFAULT_NESTED_VALUE : nested.value(),
          nested.text() == null ? new byte[0] : nested.text(),
          false);
    }
    return value;
  }

  // ------------------------------------------------------------------ encoding

  private static byte[] declaredOrderMessage(final Schema schema, final List<Object> values) {
    final var entries = new ArrayList<Entry>();
    for (final var field : schema.fields()) {
      entries.add(
          entry(
              field.key(),
              encodeValue(field, complete(field, values.get(field.index())), null, true)));
    }
    return message(entries);
  }

  @SuppressWarnings("unchecked")
  private static byte[] encodeValue(
      final FieldSpec field, final Object value, final Random random, final boolean declaredOrder) {
    return encoded(
        w -> {
          switch (field.type()) {
            case LONG -> w.writeInteger((Long) value);
            case INT -> w.writeInteger((Integer) value);
            case BOOLEAN -> w.writeBoolean((Boolean) value);
            case STRING -> w.writeString(new UnsafeBuffer((byte[]) value));
            case BINARY -> w.writeBinary(new UnsafeBuffer((byte[]) value));
            case RAW -> w.writeRaw(new UnsafeBuffer((byte[]) value));
            case ENUM ->
                w.writeString(
                    new UnsafeBuffer(KIND_NAMES[(Integer) value].getBytes(StandardCharsets.UTF_8)));
            case STRING_LIST -> {
              final var list = (List<byte[]>) value;
              w.writeArrayHeader(list.size());
              list.forEach(s -> w.writeString(new UnsafeBuffer(s)));
            }
            case LONG_LIST -> {
              final var list = (List<Long>) value;
              w.writeArrayHeader(list.size());
              list.forEach(w::writeInteger);
            }
            case NESTED -> writeNested(w, (NestedValue) value, random, declaredOrder);
            default -> throw new IllegalStateException(field.type().toString());
          }
        });
  }

  private static void writeNested(
      final MsgPackWriter w,
      final NestedValue nested,
      final Random random,
      final boolean declaredOrder) {
    final var entries = new ArrayList<Entry>();
    if (nested.value() != null) {
      entries.add(entry("value", encoded(v -> v.writeInteger(nested.value()))));
    }
    if (nested.text() != null) {
      entries.add(entry("text", encoded(v -> v.writeString(new UnsafeBuffer(nested.text())))));
    }
    if (nested.withUnknown()) {
      entries.add(entry("nu", encoded(v -> v.writeMapHeader(0))));
    }
    if (!declaredOrder) {
      Collections.shuffle(entries, random);
    }
    w.writeMapHeader(entries.size());
    for (final var e : entries) {
      w.writeString(new UnsafeBuffer(e.key()));
      w.writeRaw(new UnsafeBuffer(e.value()));
    }
  }

  private static byte[] message(final List<Entry> entries) {
    return encoded(
        w -> {
          w.writeMapHeader(entries.size());
          for (final var e : entries) {
            w.writeString(new UnsafeBuffer(e.key()));
            w.writeRaw(new UnsafeBuffer(e.value()));
          }
        });
  }

  private static byte[] encoded(final Consumer<MsgPackWriter> body) {
    final var buffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(buffer, 0);
    body.accept(writer);
    return Arrays.copyOf(buffer.byteArray(), writer.getOffset());
  }

  /** A complete msgpack value of random shape, e.g. what a newer version might add. */
  private static byte[] randomValue(final long seed) {
    final var random = new Random(seed);
    return encoded(w -> writeRandom(w, random, 2));
  }

  private static void writeRandom(final MsgPackWriter w, final Random random, final int depth) {
    switch (random.nextInt(depth > 0 ? 8 : 6)) {
      case 0 -> w.writeNil();
      case 1 -> w.writeBoolean(random.nextBoolean());
      case 2 -> w.writeInteger(random.nextLong() >> random.nextInt(64));
      case 3 -> w.writeString(new UnsafeBuffer(randomBytes(random)));
      case 4 -> w.writeBinary(new UnsafeBuffer(randomBytes(random)));
      case 5 -> w.writeFloat(random.nextDouble());
      case 6 -> {
        final int size = random.nextInt(4);
        w.writeArrayHeader(size);
        for (int i = 0; i < size; i++) {
          writeRandom(w, random, depth - 1);
        }
      }
      default -> {
        final int size = random.nextInt(4);
        w.writeMapHeader(size);
        for (int i = 0; i < size; i++) {
          w.writeString(new UnsafeBuffer(("m" + i).getBytes(StandardCharsets.UTF_8)));
          writeRandom(w, random, depth - 1);
        }
      }
    }
  }

  private static byte[] randomBytes(final Random random) {
    final var bytes = new byte[LENGTHS[random.nextInt(LENGTHS.length)]];
    random.nextBytes(bytes);
    return bytes;
  }

  // ------------------------------------------------------------------ reading back

  /** The encoded value of every top-level entry by key. */
  private static Map<String, byte[]> entriesOf(
      final DirectBuffer buffer, final int offset, final int length) {
    final var reader = new MsgPackReader().wrap(buffer, offset, length);
    // the reader works on a view that starts at the offset, so its offsets are relative to it
    final var view = reader.getBuffer();
    final var result = new LinkedHashMap<String, byte[]>();
    final int size = reader.readMapHeader();
    for (int i = 0; i < size; i++) {
      final int keyLength = reader.readStringLength();
      final var key = view.getStringWithoutLengthUtf8(reader.getOffset(), keyLength);
      reader.skipBytes(keyLength);
      final int start = reader.getOffset();
      reader.skipValue();
      final var value = new byte[reader.getOffset() - start];
      view.getBytes(start, value);
      result.put(key, value);
    }
    return result;
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

  /** Model values and field values in one comparable form. */
  @SuppressWarnings("unchecked")
  private static Object comparable(final Type type, final Object value) {
    return switch (type) {
      case LONG, INT, BOOLEAN, LONG_LIST -> value;
      case STRING, BINARY, RAW -> ByteBuffer.wrap((byte[]) value);
      case ENUM -> KIND_NAMES[(Integer) value];
      case STRING_LIST -> ((List<byte[]>) value).stream().map(ByteBuffer::wrap).toList();
      case NESTED -> {
        final var nested = (NestedValue) value;
        yield List.of(
            nested.value() == null ? DEFAULT_NESTED_VALUE : nested.value(),
            ByteBuffer.wrap(nested.text() == null ? new byte[0] : nested.text()));
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static Object actual(
      final Compiled compiled, final Object instance, final FieldSpec field)
      throws ReflectiveOperationException {
    final var value = compiled.fields().get(field.name()).get(instance);
    return switch (field.type()) {
      case LONG, INT, BOOLEAN, LONG_LIST -> value;
      case STRING, BINARY, RAW -> bytes((DirectBuffer) value);
      case ENUM -> value.toString();
      case STRING_LIST ->
          ((List<DirectBuffer>) value).stream().map(GeneratedCodecPropertyTest::bytes).toList();
      case NESTED ->
          List.of(
              compiled.innerValue().get(value),
              bytes((DirectBuffer) compiled.innerText().get(value)));
    };
  }

  private static ByteBuffer bytes(final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return ByteBuffer.wrap(bytes);
  }

  @SuppressWarnings("unchecked")
  private static void populate(
      final Compiled compiled,
      final Object instance,
      final Schema schema,
      final List<Object> values)
      throws ReflectiveOperationException {
    for (final var field : schema.fields()) {
      final var target = compiled.fields().get(field.name());
      final var value = values.get(field.index());
      switch (field.type()) {
        case LONG, INT, BOOLEAN -> target.set(instance, value);
        case STRING, BINARY, RAW -> target.set(instance, new UnsafeBuffer((byte[]) value));
        case ENUM -> target.set(instance, compiled.kind().getEnumConstants()[(Integer) value]);
        case STRING_LIST -> {
          final var list = (List<DirectBuffer>) target.get(instance);
          list.clear();
          ((List<byte[]>) value).forEach(s -> list.add(new UnsafeBuffer(s)));
        }
        case LONG_LIST -> {
          final var list = (List<Long>) target.get(instance);
          list.clear();
          list.addAll((List<Long>) value);
        }
        case NESTED -> {
          final var nested = (NestedValue) value;
          final var inner = target.get(instance);
          compiled.innerValue().set(inner, nested.value());
          compiled.innerText().set(inner, new UnsafeBuffer(nested.text()));
        }
        default -> throw new IllegalStateException(field.type().toString());
      }
    }
  }

  // ------------------------------------------------------------------ calling the codec

  /** Reads through the production path: reset, then the generated codec. */
  private static void wrap(final Object instance, final byte[] message, final int offset) {
    final var buffer = new UnsafeBuffer(new byte[offset + message.length + 8]);
    buffer.setMemory(0, buffer.capacity(), (byte) 0xc1);
    buffer.putBytes(offset, message);
    ((MsgpackBufferValue<?>) instance).wrap(buffer, offset, message.length);
  }

  private static int write(
      final Object instance, final ExpandableArrayBuffer out, final int offset) {
    final var value = (MsgpackBufferValue<?>) instance;
    final int length = value.write(out, offset);
    assertThat(length).as("write returns what getLength says").isEqualTo(value.getLength());
    return length;
  }

  // ------------------------------------------------------------------ compiling

  private static Compiled compiled(final Schema schema) {
    return COMPILED.computeIfAbsent(
        schema.source(),
        source -> {
          final var result =
              compile(
                  new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO, THREE }"),
                  new Source(
                      "sample.Inner",
                      IMPORTS
                          + """
                          @MsgpackPojo
                          public final class Inner extends MsgpackBufferValue<Inner> {
                            int value = 3;
                            DirectBuffer text = Decoding.EMPTY;
                            @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                          }
                          """),
                  new Source("sample.Sample", source));
          assertThat(result.errors()).as(source).isEmpty();
          try {
            final var sample = result.load("sample.Sample");
            final var inner = result.load("sample.Inner");
            final var fields = new LinkedHashMap<String, Field>();
            for (final var field : schema.fields()) {
              fields.put(field.name(), accessible(sample, field.name()));
            }
            return new Compiled(
                sample,
                result.load("sample.Kind"),
                fields,
                accessible(inner, "value"),
                accessible(inner, "text"));
          } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private static Field accessible(final Class<?> type, final String name)
      throws NoSuchFieldException {
    final var field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  // ------------------------------------------------------------------ model types

  private enum Type {
    LONG,
    INT,
    BOOLEAN,
    STRING,
    BINARY,
    RAW,
    ENUM,
    STRING_LIST,
    LONG_LIST,
    NESTED
  }

  /** A field named {@code f<index>}, stored under that name or under a custom key. */
  private record FieldSpec(int index, Type type, String keySuffix) {

    String name() {
      return "f" + index;
    }

    String key() {
      return keySuffix == null ? name() : "k" + index + keySuffix;
    }

    String declaration(final boolean emptyLists) {
      final var attributes = new ArrayList<String>();
      if (keySuffix != null) {
        final var escaped = key().replace("\\", "\\\\").replace("\"", "\\\"");
        attributes.add("value = \"" + escaped + "\"");
      }
      if (type == Type.BINARY) {
        attributes.add("binary = true");
      }
      if (type == Type.RAW) {
        attributes.add("raw = true");
      }
      final var annotation =
          attributes.isEmpty() ? "" : "@MsgpackProperty(" + String.join(", ", attributes) + ") ";
      return annotation
          + switch (type) {
            case LONG -> "long " + name() + " = -7L;";
            case INT -> "int " + name() + " = 5;";
            case BOOLEAN -> "boolean " + name() + " = true;";
            case STRING, BINARY ->
                "DirectBuffer " + name() + " = new UnsafeBuffer(new byte[] {'d', 1});";
            case RAW -> "DirectBuffer " + name() + " = new UnsafeBuffer(new byte[] {(byte) 0xc0});";
            case ENUM -> "Kind " + name() + " = Kind.TWO;";
            case STRING_LIST ->
                "final List<DirectBuffer> "
                    + name()
                    + " = new ArrayList<>("
                    + (emptyLists ? "" : "List.of(new UnsafeBuffer(new byte[] {'x'}))")
                    + ");";
            case LONG_LIST ->
                "final List<Long> "
                    + name()
                    + " = new ArrayList<>("
                    + (emptyLists ? "" : "List.of(9L)")
                    + ");";
            case NESTED -> "final Inner " + name() + " = new Inner();";
          };
    }
  }

  /**
   * @param emptyLists whether list fields start empty, as an UnpackedObject's ArrayProperty does
   */
  private record Schema(List<FieldSpec> fields, boolean emptyLists) {

    String source() {
      final var s = new StringBuilder(IMPORTS);
      s.append("@MsgpackPojo public final class Sample extends MsgpackBufferValue<Sample> {\n");
      for (final var field : fields) {
        s.append("  ").append(field.declaration(emptyLists)).append('\n');
      }
      s.append(
          "  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();\n");
      return s.append("}\n").toString();
    }
  }

  /** {@code null} parts are not written, so the nested object keeps their default. */
  private record NestedValue(Integer value, byte[] text, boolean withUnknown) {}

  private record UnknownEntry(String suffix, byte[] value) {}

  private record Scenario(
      Schema schema,
      List<Object> values,
      List<Object> previousValues,
      List<Boolean> present,
      long orderSeed,
      List<UnknownEntry> unknowns,
      int offset) {}

  private record Entry(byte[] key, byte[] value) {}

  private record Compiled(
      Class<?> sample,
      Class<?> kind,
      Map<String, Field> fields,
      Field innerValue,
      Field innerText) {

    Object newInstance() throws ReflectiveOperationException {
      return sample.getConstructor().newInstance();
    }
  }

  /** The UnpackedObject equivalent of a generated shape. */
  private static final class Twin extends UnpackedObject {
    private final Map<String, BaseProperty<?>> properties = new LinkedHashMap<>();

    Twin(final Schema schema) {
      super(schema.fields().size());
      for (final var field : schema.fields()) {
        final var key = field.key();
        final BaseProperty<?> property =
            switch (field.type()) {
              case LONG -> new LongProperty(key, -7L);
              case INT -> new IntegerProperty(key, 5);
              case BOOLEAN -> new BooleanProperty(key, true);
              case STRING -> new StringProperty(key, "d\u0001");
              case BINARY -> new BinaryProperty(key, new UnsafeBuffer(new byte[] {'d', 1}));
              case RAW -> new PackedProperty(key, new UnsafeBuffer(new byte[] {(byte) 0xc0}));
              case ENUM -> new EnumProperty<>(key, TwinKind.class, TwinKind.TWO);
              case STRING_LIST -> new ArrayProperty<>(key, StringValue::new);
              case LONG_LIST -> new ArrayProperty<>(key, LongValue::new);
              case NESTED -> new ObjectProperty<>(key, new TwinInner());
            };
        properties.put(field.name(), property);
        declareProperty(property);
      }
    }

    /** The field in the form of {@link #comparable}. */
    Object actual(final FieldSpec field) {
      final var property = properties.get(field.name());
      return switch (field.type()) {
        case LONG -> ((LongProperty) property).getValue();
        case INT -> ((IntegerProperty) property).getValue();
        case BOOLEAN -> ((BooleanProperty) property).getValue();
        case STRING -> bytes(((StringProperty) property).getValue());
        case BINARY -> bytes(((BinaryProperty) property).getValue());
        case RAW -> bytes(((PackedProperty) property).getValue());
        case ENUM -> ((EnumProperty<?>) property).getValue().toString();
        case STRING_LIST -> {
          final var values = new ArrayList<ByteBuffer>();
          ((ArrayProperty<StringValue>) property).forEach(v -> values.add(bytes(v.getValue())));
          yield values;
        }
        case LONG_LIST -> {
          final var values = new ArrayList<Long>();
          ((ArrayProperty<LongValue>) property).forEach(v -> values.add(v.getValue()));
          yield values;
        }
        case NESTED -> {
          final var inner = ((ObjectProperty<TwinInner>) property).getValue();
          yield List.of(inner.value.getValue(), bytes(inner.text.getValue()));
        }
      };
    }
  }

  private static final class TwinInner extends UnpackedObject {
    private final IntegerProperty value = new IntegerProperty("value", DEFAULT_NESTED_VALUE);
    private final StringProperty text = new StringProperty("text", "");

    TwinInner() {
      super(2);
      declareProperty(value).declareProperty(text);
    }
  }

  private enum TwinKind {
    ONE,
    TWO,
    THREE
  }
}
