/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackFormat;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackType;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/** Helpers called by generated deserializers. Not meant to be used directly. */
public final class Decoding {
  public static final DirectBuffer EMPTY = new UnsafeBuffer(new byte[0]);

  private Decoding() {}

  /**
   * Compares two buffers by their bytes, whatever their class, like the {@code StringValue}, {@code
   * BinaryValue} and {@code PackedValue} they replace.
   */
  public static boolean equalContent(
      final @Nullable DirectBuffer a, final @Nullable DirectBuffer b) {
    return a == b || a != null && b != null && BufferUtil.contentsEqual(a, b);
  }

  /** Hash code of the bytes of a buffer, consistent with {@link #equalContent}. */
  public static int contentHash(final @Nullable DirectBuffer buffer) {
    if (buffer == null) {
      return 0;
    }
    int hash = 1;
    for (int i = 0; i < buffer.capacity(); i++) {
      hash = 31 * hash + buffer.getByte(i);
    }
    return hash;
  }

  public static boolean equalContents(
      final List<? extends DirectBuffer> a, final List<? extends DirectBuffer> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!equalContent(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static int contentHash(final List<? extends DirectBuffer> buffers) {
    int hash = 1;
    for (int i = 0; i < buffers.size(); i++) {
      hash = 31 * hash + contentHash(buffers.get(i));
    }
    return hash;
  }

  public static UnsafeBuffer encodedKey(final String key) {
    return new UnsafeBuffer(key.getBytes(StandardCharsets.UTF_8));
  }

  /** Reads an integer, rejecting values outside the {@code int} range like {@code IntegerValue}. */
  public static int readInt(final MsgPackReader reader) {
    final long value = reader.readInteger();
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "Expected an int value, but got %d, which is out of range".formatted(value));
    }
    return (int) value;
  }

  /**
   * Reads a string as a new view on the source buffer, like the StringProperty it replaces: nothing
   * is copied, so the result is only valid while the source buffer is not reused. Allocates the
   * view; {@link #readString(MsgPackReader, UnsafeBuffer)} does not.
   */
  public static UnsafeBuffer readString(final MsgPackReader reader) {
    return readString(reader, new UnsafeBuffer(0, 0));
  }

  /** Wraps {@code target} around a string of the source buffer, see {@link #readString}. */
  public static UnsafeBuffer readString(final MsgPackReader reader, final UnsafeBuffer target) {
    final int length = reader.readStringLength();
    final int offset = reader.getOffset();
    reader.skipBytes(length);
    target.wrap(reader.getBuffer(), offset, length);
    return target;
  }

  /** Reads a binary as a new view on the source buffer, see {@link #readString}. */
  public static UnsafeBuffer readBinary(final MsgPackReader reader) {
    return readBinary(reader, new UnsafeBuffer(0, 0));
  }

  /** Wraps {@code target} around a binary of the source buffer, see {@link #readString}. */
  public static UnsafeBuffer readBinary(final MsgPackReader reader, final UnsafeBuffer target) {
    final int length = reader.readBinaryLength();
    final int offset = reader.getOffset();
    reader.skipBytes(length);
    target.wrap(reader.getBuffer(), offset, length);
    return target;
  }

  /**
   * Reads one complete msgpack value as a new view on the source buffer, see {@link #readString}.
   */
  public static UnsafeBuffer readRaw(final MsgPackReader reader) {
    return readRaw(reader, new UnsafeBuffer(0, 0));
  }

  /** Wraps {@code target} around one complete msgpack value, see {@link #readString}. */
  public static UnsafeBuffer readRaw(final MsgPackReader reader, final UnsafeBuffer target) {
    final int start = reader.getOffset();
    reader.skipValue();
    target.wrap(reader.getBuffer(), start, reader.getOffset() - start);
    return target;
  }

  /**
   * Returns the document to store for {@code value}, like {@code DocumentValue#wrap} does: an empty
   * or nil value is the empty document {@link DocumentValue#EMPTY_DOCUMENT} (shared, never modify
   * it), a map is returned as it is. Call it in a setter of a {@code document} field to keep the
   * guarantee of the {@code DocumentProperty} that only a map can be stored.
   *
   * @throws IllegalArgumentException if the value is not a msgpack map
   */
  public static DirectBuffer document(final DirectBuffer value) {
    final int length = value.capacity();
    if (length == 0 || (length == 1 && value.getByte(0) == MsgPackCodes.NIL)) {
      return DocumentValue.EMPTY_DOCUMENT;
    }
    final MsgPackType type = MsgPackFormat.valueOf(value.getByte(0)).getType();
    if (type != MsgPackType.MAP) {
      throw new IllegalArgumentException(
          "Expected document to be a root level object, but was '%s'".formatted(type.name()));
    }
    return value;
  }

  /**
   * Reads a document, which is stored as a binary, as a new view on the source buffer, see {@link
   * #readString} and {@link #document}.
   */
  public static UnsafeBuffer readDocument(final MsgPackReader reader) {
    return readDocument(reader, new UnsafeBuffer(0, 0));
  }

  /** Wraps {@code target} around a document of the source buffer, see {@link #readDocument}. */
  public static UnsafeBuffer readDocument(final MsgPackReader reader, final UnsafeBuffer target) {
    readBinary(reader, target);
    final DirectBuffer document = document(target);
    if (document != target) {
      target.wrap(document);
    }
    return target;
  }

  /**
   * Returns a view on a new array holding the bytes of {@code source}, or null for null. Used to
   * copy a string, binary, raw value or document, which are only views on another buffer.
   */
  public static @Nullable UnsafeBuffer copy(final @Nullable DirectBuffer source) {
    if (source == null) {
      return null;
    }
    final byte[] bytes = new byte[source.capacity()];
    source.getBytes(0, bytes);
    return new UnsafeBuffer(bytes);
  }

  /** Wraps {@code target} around a new array holding the bytes of {@code source}. */
  public static void copyInto(final DirectBuffer source, final UnsafeBuffer target) {
    final byte[] bytes = new byte[source.capacity()];
    source.getBytes(0, bytes);
    target.wrap(bytes);
  }

  /**
   * Copies a nested value that is not a generated POJO and has no copy of its own: it is written to
   * a new array and read from it. {@code target} is reset first and then references the array,
   * which nothing else uses, so it is independent of {@code source}.
   */
  public static void copyValue(final BaseValue source, final BaseValue target) {
    final int length = source.getEncodedLength();
    final var buffer = new UnsafeBuffer(new byte[length]);
    source.write(MsgpackScratch.writer(buffer, 0));
    target.reset();
    target.read(MsgpackScratch.reader(buffer, 0, length));
  }

  /** Appends a string as JSON like {@code StringValue}: quoted, and not escaped. */
  public static void appendString(final StringBuilder builder, final @Nullable DirectBuffer value) {
    if (value == null) {
      builder.append("null");
    } else {
      builder.append('"').append(value.getStringWithoutLengthUtf8(0, value.capacity())).append('"');
    }
  }

  /** Appends a binary or document as JSON like {@code BinaryValue}: quoted base64. */
  public static void appendBinary(final StringBuilder builder, final @Nullable DirectBuffer value) {
    if (value == null) {
      builder.append("null");
      return;
    }
    final byte[] bytes = new byte[value.capacity()];
    value.getBytes(0, bytes);
    builder.append('"').append(Base64.getEncoder().encodeToString(bytes)).append('"');
  }

  /** Appends a raw value as JSON like {@code PackedValue}: only its length. */
  public static void appendRaw(final StringBuilder builder, final @Nullable DirectBuffer value) {
    builder
        .append("[packed value (length=")
        .append(value == null ? 0 : value.capacity())
        .append(")]");
  }

  /** Appends an enum constant as JSON like {@code EnumValue}: quoted name, or null. */
  public static void appendEnum(final StringBuilder builder, final @Nullable Enum<?> value) {
    if (value == null) {
      builder.append("null");
    } else {
      builder.append('"').append(value).append('"');
    }
  }

  /** Reads a string and resolves it to the constant with the same name, without allocating. */
  public static <E extends Enum<E>> E readEnum(
      final MsgPackReader reader, final UnsafeBuffer[] names, final E[] values) {
    final int length = reader.readStringLength();
    final int offset = reader.getOffset();
    reader.skipBytes(length);
    final DirectBuffer buffer = reader.getBuffer();
    for (int i = 0; i < names.length; i++) {
      final UnsafeBuffer name = names[i];
      if (name.capacity() == length && equalBytes(name, buffer, offset, length)) {
        return values[i];
      }
    }
    throw new IllegalArgumentException(
        "No enum constant of %s named '%s'"
            .formatted(
                values.getClass().getComponentType().getSimpleName(),
                buffer.getStringWithoutLengthUtf8(offset, length)));
  }

  /** Names the property whose value could not be read, like {@code ObjectValue} does. */
  public static RuntimeException propertyFailure(
      final DirectBuffer buffer, final int keyOffset, final int keyLength, final Throwable cause) {
    return new RuntimeException(
        "Could not read property '%s'"
            .formatted(buffer.getStringWithoutLengthUtf8(keyOffset, keyLength)),
        cause);
  }

  public static boolean keyEquals(
      final UnsafeBuffer key, final DirectBuffer buffer, final int offset, final int length) {
    return key.capacity() == length && equalBytes(key, buffer, offset, length);
  }

  private static boolean equalBytes(
      final DirectBuffer expected, final DirectBuffer actual, final int offset, final int length) {
    for (int i = 0; i < length; i++) {
      if (expected.getByte(i) != actual.getByte(offset + i)) {
        return false;
      }
    }
    return true;
  }
}
