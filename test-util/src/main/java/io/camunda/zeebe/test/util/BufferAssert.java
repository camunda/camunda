/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import static io.camunda.zeebe.util.buffer.BufferUtil.NO_WRAP;
import static io.camunda.zeebe.util.buffer.BufferUtil.bytesAsHexString;

import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.AbstractAssert;

public final class BufferAssert extends AbstractAssert<BufferAssert, DirectBuffer> {

  protected BufferAssert(final DirectBuffer actual) {
    super(actual, BufferAssert.class);
  }

  public static BufferAssert assertThatBuffer(final DirectBuffer buffer) {
    return new BufferAssert(buffer);
  }

  public BufferAssert hasBytes(final byte[] expected, final int position) {
    isNotNull();

    final byte[] actualBytes = new byte[expected.length];

    try {
      actual.getBytes(position, actualBytes, 0, actualBytes.length);
    } catch (final Exception e) {
      failWithMessage(
          "Unable to read %d bytes from actual: %s", actualBytes.length, e.getMessage());
    }

    if (!Arrays.equals(expected, actualBytes)) {
      failWithMessage(
          "Expected byte array match bytes <%s> but was <%s>",
          bytesAsHexString(expected, NO_WRAP), bytesAsHexString(actualBytes, NO_WRAP));
    }

    return this;
  }

  public BufferAssert hasBytes(final byte[] expected) {
    return hasBytes(expected, 0);
  }

  public BufferAssert hasBytes(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    buffer.getBytes(offset, bytes);
    return hasBytes(bytes);
  }

  public BufferAssert hasBytes(final DirectBuffer buffer) {
    return hasBytes(buffer, 0, buffer.capacity());
  }

  public BufferAssert hasCapacity(final int expectedCapacity) {
    isNotNull();

    if (expectedCapacity != actual.capacity()) {
      failWithMessage("Expected capacity " + expectedCapacity + " but was " + actual.capacity());
    }

    return this;
  }

  public BufferAssert hasBytes(final BufferWriter writer) {
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
    writer.write(buffer, 0);

    return hasBytes(buffer);
  }
}
