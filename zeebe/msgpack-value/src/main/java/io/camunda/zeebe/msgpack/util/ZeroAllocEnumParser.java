/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * Zero-allocation enum parser using a DirectBufferView as the map key.
 *
 * @param <E> the enum type
 */
public class ZeroAllocEnumParser<E extends Enum<E>> implements EnumParser<E> {
  private final Map<DirectBufferView, E> lookup;
  // Thread-local view for parse() to avoid allocation
  private final ThreadLocal<DirectBufferView> threadView =
      ThreadLocal.withInitial(DirectBufferView::new);

  public ZeroAllocEnumParser(final Class<E> enumClass) {
    final int constantsCount = (int) Arrays.stream(enumClass.getEnumConstants()).count();
    lookup = constantsCount > 16 ? new HashMap<>() : new TreeMap<>();
    for (final E e : enumClass.getEnumConstants()) {
      final byte[] bytes = e.name().getBytes(StandardCharsets.US_ASCII);
      final DirectBufferView key = new DirectBufferView();
      key.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
      lookup.put(key, e);
    }
  }

  @Override
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    final DirectBufferView view = threadView.get();
    view.wrap(buffer, offset, length);
    return lookup.get(view);
  }

  /** A view of a DirectBuffer region for use as a map key. Does not allocate or copy data. */
  private static final class DirectBufferView implements Comparable<DirectBufferView> {
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
    public int compareTo(final ZeroAllocEnumParser.DirectBufferView o) {
      return buffer.compareTo(o.buffer);
    }
  }
}
