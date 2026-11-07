/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.io.InputStream;
import java.util.Arrays;

/** Returns the given number of zero bytes without allocating them up front. */
final class ZeroInputStream extends InputStream {

  private long remaining;

  ZeroInputStream(final long size) {
    remaining = size;
  }

  @Override
  public int read() {
    if (remaining == 0) {
      return -1;
    }
    remaining--;
    return 0;
  }

  @Override
  public int read(final byte[] buffer, final int offset, final int length) {
    if (remaining == 0) {
      return -1;
    }
    final int count = (int) Math.min(length, remaining);
    Arrays.fill(buffer, offset, offset + count, (byte) 0);
    remaining -= count;
    return count;
  }
}
