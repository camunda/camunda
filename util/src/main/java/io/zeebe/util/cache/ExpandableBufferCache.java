/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.cache;

import java.nio.ByteBuffer;
import java.util.function.LongFunction;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * LRU-cache for buffers. The buffers can have a different size. If the buffer size is greater than
 * the given initial size then the underlying buffer is expanded.
 *
 * <p>Inspired by agrona's LongLruCache.
 */
public class ExpandableBufferCache {
  private final DirectBuffer readBuffer = new UnsafeBuffer(0, 0);

  private final long keys[];
  private final MutableDirectBuffer[] values;

  private final LongFunction<DirectBuffer> lookup;

  private final int capacity;
  private int size;

  /**
   * Create a new cache.
   *
   * @param cacheCapacity capacity of the cache
   * @param initialBufferCapacity initial capacity of the underlying expandable buffers
   * @param lookup a function for lookup an absent value
   */
  public ExpandableBufferCache(
      int cacheCapacity, int initialBufferCapacity, LongFunction<DirectBuffer> lookup) {
    this.capacity = cacheCapacity;
    this.lookup = lookup;

    size = 0;
    keys = new long[cacheCapacity];

    values = new MutableDirectBuffer[cacheCapacity];
    for (int i = 0; i < values.length; i++) {
      values[i] = new ExpandableDirectByteBuffer(initialBufferCapacity);
    }
  }

  public DirectBuffer get(long key) {
    final int index = indexOf(key);
    if (index >= 0) {
      final MutableDirectBuffer value = values[index];

      makeMostRecent(key, value, index);

      final ByteBuffer byteBuffer = value.byteBuffer();
      // wrap the buffer to the original size
      readBuffer.wrap(byteBuffer, 0, byteBuffer.limit());

      return readBuffer;
    } else {
      final DirectBuffer buffer = lookup.apply(key);
      if (buffer != null) {
        insert(key, buffer);
      }
      return buffer;
    }
  }

  private int indexOf(long key) {
    for (int i = 0; i < size; i++) {
      if (keys[i] == key) {
        return i;
      }
    }
    return -1;
  }

  private void insert(long key, final DirectBuffer buffer) {
    final MutableDirectBuffer value;
    if (size == capacity) {
      // drop the least recently used
      value = values[size - 1];
      if (buffer.capacity() < value.byteBuffer().limit()) {
        recycle(value);
      }
    } else {
      value = values[size];

      size += 1;
    }

    copyBuffer(buffer, value);

    makeMostRecent(key, value, size - 1);
  }

  private void copyBuffer(final DirectBuffer source, final MutableDirectBuffer target) {
    source.getBytes(0, target, 0, source.capacity());
    // use the limit to indicate the buffer length
    target.byteBuffer().limit(source.capacity());
  }

  private void makeMostRecent(long key, MutableDirectBuffer value, int fromIndex) {
    // shift cache entries to right (tail)
    for (int i = fromIndex; i > 0; i--) {
      keys[i] = keys[i - 1];
      values[i] = values[i - 1];
    }

    keys[0] = key;
    values[0] = value;
  }

  private void recycle(MutableDirectBuffer buffer) {
    buffer.setMemory(0, buffer.capacity(), (byte) 0);
  }

  public void put(long key, DirectBuffer buffer) {
    final int index = indexOf(key);
    if (index >= 0) {
      final MutableDirectBuffer value = values[index];

      if (buffer.capacity() < value.byteBuffer().limit()) {
        recycle(value);
      }
      copyBuffer(buffer, value);

      makeMostRecent(key, value, index);
    } else {
      insert(key, buffer);
    }
  }

  public void remove(long key) {
    final int index = indexOf(key);
    if (index >= 0) {
      final MutableDirectBuffer value = values[index];
      recycle(value);

      size -= 1;

      // shift cache entries to left (head)
      for (int i = index; i < size; i++) {
        keys[i] = keys[i + 1];
        values[i] = values[i + 1];
      }

      keys[size] = 0;
      values[size] = value;
    }
  }

  public int getSize() {
    return size;
  }

  public void clear() {
    for (int i = 0; i < size; i++) {
      recycle(values[i]);
    }

    size = 0;
  }
}
