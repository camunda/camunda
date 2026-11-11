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

  /** Reads a string into a buffer the caller owns; the source buffer may be reused afterwards. */
  public static DirectBuffer readString(final MsgPackReader reader) {
    final int length = reader.readStringLength();
    final int offset = reader.getOffset();
    reader.skipBytes(length);
    return copy(reader.getBuffer(), offset, length);
  }

  /** Reads one complete msgpack value as bytes into a buffer the caller owns. */
  public static DirectBuffer readRaw(final MsgPackReader reader) {
    final int start = reader.getOffset();
    reader.skipValue();
    return copy(reader.getBuffer(), start, reader.getOffset() - start);
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

  private static DirectBuffer copy(final DirectBuffer source, final int offset, final int length) {
    if (length == 0) {
      return EMPTY;
    }
    final byte[] bytes = new byte[length];
    source.getBytes(offset, bytes, 0, length);
    return new UnsafeBuffer(bytes);
  }
}
