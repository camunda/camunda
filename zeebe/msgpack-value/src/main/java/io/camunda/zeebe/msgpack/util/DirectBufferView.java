/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** A view of a DirectBuffer region for use as a map key. Does not allocate or copy data. */
final class DirectBufferView implements Comparable<DirectBufferView> {
  private DirectBuffer buffer;

  public DirectBufferView() {}

  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    if (buffer.capacity() == length && offset == 0) {
      this.buffer = buffer;
    } else {
      this.buffer = new UnsafeBuffer(buffer, offset, length);
    }
  }

  @Override
  public int hashCode() {
    final int length = buffer.capacity();
    int h = 1;
    int i = 0;
    // Vectorized: process 8 bytes at a time
    for (; i < (length & ~7); i += 8) {
      final long l = buffer.getLong(i);
      h = 31 * h + Long.hashCode(l);
    }
    // Vectorized: process 4 bytes at a time
    for (; i < (length & ~3); i += 4) {
      h = 31 * h + buffer.getInt(i);
    }
    // Process remaining bytes
    for (; i < length; i++) {
      h = 31 * h + buffer.getByte(i);
    }
    return h;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof final DirectBufferView other)) {
      return false;
    }
    return buffer.compareTo(other.buffer) == 0;
  }

  @Override
  public int compareTo(final DirectBufferView o) {
    return buffer.compareTo(o.buffer);
  }
}
