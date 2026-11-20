/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.encoded;
import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.DEFAULT_NESTED_VALUE;
import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.KIND_NAMES;
import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.LENGTHS;
import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.complete;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Compiled;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.FieldSpec;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.NestedValue;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Scenario;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Schema;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Type;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.UnknownEntry;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The independent model of the msgpack format that {@link GeneratedCodecPropertyTest} checks the
 * generated codec against. It writes the bytes with the {@link MsgPackWriter} and reads the fields
 * back with reflection, so it shares no code with the generator.
 */
final class ReferenceModel {

  private ReferenceModel() {}

  private static Entry entry(final String key, final byte[] value) {
    return new Entry(key.getBytes(StandardCharsets.UTF_8), value);
  }

  static String unknownKey(final int index, final UnknownEntry unknown) {
    return "u" + index + unknown.suffix();
  }

  /**
   * Keys in random order, some of them missing, with unknown entries in between.
   *
   * @param nestedRequired write the nested values even when the scenario leaves them out, because
   *     an UnpackedObject has no default for an object property and rejects a message without it
   */
  static byte[] randomOrderMessage(final Scenario scenario, final boolean nestedRequired) {
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

  // ------------------------------------------------------------------ encoding

  static byte[] declaredOrderMessage(final Schema schema, final List<Object> values) {
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
            case DOCUMENT -> w.writeBinary(new UnsafeBuffer((byte[]) value));
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
            case POJO_LIST, VALUE_LIST -> {
              final var list = (List<NestedValue>) value;
              w.writeArrayHeader(list.size());
              list.forEach(item -> writeNested(w, item, random, declaredOrder));
            }
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

  /** A complete msgpack value of random shape, e.g. what a newer version might add. */
  static byte[] randomValue(final long seed) {
    final var random = new Random(seed);
    return encoded(w -> writeRandom(w, random, 2));
  }

  /** A document: a msgpack map of random content, which is what a DocumentProperty holds. */
  static byte[] randomDocument(final long seed) {
    final var random = new Random(seed);
    return encoded(
        w -> {
          final int size = random.nextInt(4);
          w.writeMapHeader(size);
          for (int i = 0; i < size; i++) {
            w.writeString(new UnsafeBuffer(("d" + i).getBytes(StandardCharsets.UTF_8)));
            writeRandom(w, random, 2);
          }
        });
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
  static Map<String, byte[]> entriesOf(
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

  /** Model values and field values in one comparable form. */
  @SuppressWarnings("unchecked")
  static Object comparable(final Type type, final Object value) {
    return switch (type) {
      case LONG, INT, BOOLEAN, LONG_LIST -> value;
      case STRING, BINARY, RAW, DOCUMENT -> ByteBuffer.wrap((byte[]) value);
      case ENUM -> KIND_NAMES[(Integer) value];
      case STRING_LIST -> ((List<byte[]>) value).stream().map(ByteBuffer::wrap).toList();
      case POJO_LIST, VALUE_LIST ->
          ((List<NestedValue>) value).stream().map(item -> comparable(Type.NESTED, item)).toList();
      case NESTED -> {
        final var nested = (NestedValue) value;
        yield List.of(
            nested.value() == null ? DEFAULT_NESTED_VALUE : nested.value(),
            ByteBuffer.wrap(nested.text() == null ? new byte[0] : nested.text()));
      }
    };
  }

  @SuppressWarnings("unchecked")
  static Object actual(final Compiled compiled, final Object instance, final FieldSpec field)
      throws ReflectiveOperationException {
    final var value = compiled.fields().get(field.name()).get(instance);
    return switch (field.type()) {
      case LONG, INT, BOOLEAN, LONG_LIST -> value;
      case STRING, BINARY, RAW, DOCUMENT -> bytes((DirectBuffer) value);
      case ENUM -> value.toString();
      case STRING_LIST -> ((List<DirectBuffer>) value).stream().map(ReferenceModel::bytes).toList();
      case NESTED ->
          List.of(
              compiled.innerValue().get(value),
              bytes((DirectBuffer) compiled.innerText().get(value)));
      case POJO_LIST ->
          ((List<Object>) value)
              .stream()
                  .map(
                      item -> {
                        try {
                          return (Object)
                              List.of(
                                  compiled.innerValue().get(item),
                                  bytes((DirectBuffer) compiled.innerText().get(item)));
                        } catch (final ReflectiveOperationException e) {
                          throw new IllegalStateException(e);
                        }
                      })
                  .toList();
      case VALUE_LIST ->
          ((List<Object>) value)
              .stream()
                  .map(
                      item -> {
                        try {
                          return (Object)
                              List.of(
                                  compiled.legacy().getMethod("value").invoke(item),
                                  bytes(
                                      (DirectBuffer)
                                          compiled.legacy().getMethod("text").invoke(item)));
                        } catch (final ReflectiveOperationException e) {
                          throw new IllegalStateException(e);
                        }
                      })
                  .toList();
    };
  }

  static ByteBuffer bytes(final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return ByteBuffer.wrap(bytes);
  }

  @SuppressWarnings("unchecked")
  static void populate(
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
        case STRING, BINARY, RAW, DOCUMENT ->
            target.set(instance, new UnsafeBuffer((byte[]) value));
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
        case POJO_LIST, VALUE_LIST -> {
          final var list = (List<Object>) target.get(instance);
          list.clear();
          for (final var item : (List<NestedValue>) value) {
            final var bytes = encoded(w -> writeNested(w, item, null, true));
            if (field.type() == Type.POJO_LIST) {
              final var created =
                  compiled.innerValue().getDeclaringClass().getConstructor().newInstance();
              ((MsgpackBufferValue<?>) created).wrap(new UnsafeBuffer(bytes), 0, bytes.length);
              list.add(created);
            } else {
              final var created = compiled.legacy().getConstructor().newInstance();
              ((UnpackedObject) created).wrap(new UnsafeBuffer(bytes));
              list.add(created);
            }
          }
        }
        default -> throw new IllegalStateException(field.type().toString());
      }
    }
  }

  // ------------------------------------------------------------------ calling the codec

  /** Reads through the production path: reset, then the generated codec. */
  static void wrap(final Object instance, final byte[] message, final int offset) {
    final var buffer = new UnsafeBuffer(new byte[offset + message.length + 8]);
    buffer.setMemory(0, buffer.capacity(), (byte) 0xc1);
    buffer.putBytes(offset, message);
    ((MsgpackBufferValue<?>) instance).wrap(buffer, offset, message.length);
  }

  static int write(final Object instance, final ExpandableArrayBuffer out, final int offset) {
    final var value = (MsgpackBufferValue<?>) instance;
    final int length = value.write(out, offset);
    assertThat(length).as("write returns what getLength says").isEqualTo(value.getLength());
    return length;
  }

  private record Entry(byte[] key, byte[] value) {}
}
