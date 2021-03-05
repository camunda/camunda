/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.mockito.ArgumentMatcher;

public final class BufferMatcher implements ArgumentMatcher<DirectBuffer> {
  protected byte[] expectedBytes;
  protected int position = 0;

  @Override
  public boolean matches(final DirectBuffer argument) {
    if (argument == null) {
      return false;
    }

    final byte[] actualBytes = new byte[expectedBytes.length];

    // TODO: try-catch in case buffer has not expected size
    argument.getBytes(position, actualBytes, 0, actualBytes.length);

    return Arrays.equals(expectedBytes, actualBytes);
  }

  public static BufferMatcher hasBytes(final byte[] bytes) {
    final BufferMatcher matcher = new BufferMatcher();

    matcher.expectedBytes = bytes;

    return matcher;
  }

  public BufferMatcher atPosition(final int position) {
    this.position = position;
    return this;
  }
}
