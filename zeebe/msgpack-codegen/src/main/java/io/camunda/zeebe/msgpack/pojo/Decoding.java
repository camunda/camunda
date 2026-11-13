/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Helpers called by generated deserializers. Not meant to be used directly. */
public final class Decoding {
  public static final DirectBuffer EMPTY = new UnsafeBuffer(new byte[0]);

  private Decoding() {}

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
