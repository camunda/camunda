/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

final class TestUtils {

  static String readString(final Consumer<BufferReader> reader) {
    final var value = new StringReader();
    reader.accept(value);
    return value.value;
  }

  private static final class StringReader implements BufferReader {
    private String value;

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      value = BufferUtil.bufferAsString(buffer, offset, length);
    }
  }
}
