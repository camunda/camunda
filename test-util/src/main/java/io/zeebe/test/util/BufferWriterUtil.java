/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.ReflectUtil;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

public final class BufferWriterUtil {

  public static <T extends BufferWriter & BufferReader> void assertEqualFieldsAfterWriteAndRead(
      final T writer, final String... fieldNames) {
    final T reader = writeAndRead(writer);

    assertThat(reader).isEqualToComparingOnlyGivenFields(writer, fieldNames);
  }

  public static <T extends BufferWriter & BufferReader> T writeAndRead(final T writer) {
    @SuppressWarnings("unchecked")
    final T reader = ReflectUtil.newInstance((Class<T>) writer.getClass());

    wrap(writer, reader);

    return reader;
  }

  public static void wrap(final BufferWriter writer, final BufferReader reader) {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
    writer.write(buffer, 0);

    reader.wrap(buffer, 0, buffer.capacity());
  }

  public static <T extends BufferReader> T wrap(
      final BufferWriter writer, final Class<T> readerClass) {
    final T reader = ReflectUtil.newInstance(readerClass);

    wrap(writer, reader);

    return reader;
  }
}
