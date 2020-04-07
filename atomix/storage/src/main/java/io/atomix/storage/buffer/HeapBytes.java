/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.buffer;

import java.nio.ByteBuffer;

/** {@link ByteBuffer} based heap bytes. */
public class HeapBytes extends ByteBufferBytes {
  public static final byte[] EMPTY = new byte[0];

  protected HeapBytes(final ByteBuffer buffer) {
    super(buffer);
  }

  /**
   * Allocates a new heap byte array.
   *
   * @param size The count of the buffer to allocate (in bytes).
   * @return The heap buffer.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed count for
   *     an array on the Java heap - {@code Integer.MAX_VALUE - 5}
   */
  public static HeapBytes allocate(final int size) {
    if (size > MAX_SIZE) {
      throw new IllegalArgumentException(
          "size cannot for HeapBytes cannot be greater than " + MAX_SIZE);
    }
    return new HeapBytes(ByteBuffer.allocate((int) size));
  }

  /**
   * Wraps the given bytes in a {@link HeapBytes} object.
   *
   * <p>The returned {@link Bytes} object will be backed by a {@link ByteBuffer} instance that wraps
   * the given byte array. The {@link Bytes#size()} will be equivalent to the provided by array
   * {@code length}.
   *
   * @param bytes The bytes to wrap.
   */
  public static HeapBytes wrap(final byte[] bytes) {
    return new HeapBytes(ByteBuffer.wrap(bytes));
  }

  @Override
  protected ByteBuffer newByteBuffer(final int size) {
    return ByteBuffer.allocate((int) size);
  }

  @Override
  public boolean hasArray() {
    return true;
  }
}
