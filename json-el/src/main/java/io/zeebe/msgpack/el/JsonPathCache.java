/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.el;

import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JsonPathCache {
  private static final int INITIAL_CAPACITY = 12;

  private int size = 0;
  private int capacity = INITIAL_CAPACITY;

  private int[] keys = new int[INITIAL_CAPACITY];

  private int[] offsets = new int[INITIAL_CAPACITY];
  private int[] lengths = new int[INITIAL_CAPACITY];

  private final DirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer bufferView = new UnsafeBuffer(0, 0);

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
