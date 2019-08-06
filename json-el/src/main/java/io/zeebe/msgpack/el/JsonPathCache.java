/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.el;

import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JsonPathCache {
  private static final int INITIAL_CAPACITY = 12;
  private final DirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer bufferView = new UnsafeBuffer(0, 0);
  private int size = 0;
  private int capacity = INITIAL_CAPACITY;
  private int[] keys = new int[INITIAL_CAPACITY];
  private int[] offsets = new int[INITIAL_CAPACITY];
  private int[] lengths = new int[INITIAL_CAPACITY];

  public void wrap(DirectBuffer buffer) {
    valueBuffer.wrap(buffer);

    size = 0;
  }

  public DirectBuffer get(int key) {
    for (int k = 0; k < size; k++) {
      if (key == keys[k]) {
        final int offset = offsets[k];
        final int length = lengths[k];

        bufferView.wrap(valueBuffer, offset, length);

        return bufferView;
      }
    }
    return null;
  }

  public void put(int key, int offset, int length) {
    if (size > capacity) {
      capacity = capacity * 2;

      keys = Arrays.copyOf(keys, capacity);
      offsets = Arrays.copyOf(offsets, capacity);
      lengths = Arrays.copyOf(lengths, capacity);
    }

    keys[size] = key;
    offsets[size] = offset;
    lengths[size] = length;

    size += 1;
  }

  public int size() {
    return size;
  }

  public void reset() {
    capacity = INITIAL_CAPACITY;
    keys = new int[INITIAL_CAPACITY];
    offsets = new int[INITIAL_CAPACITY];
    lengths = new int[INITIAL_CAPACITY];
  }
}
