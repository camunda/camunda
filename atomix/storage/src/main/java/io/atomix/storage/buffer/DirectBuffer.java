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

import static com.google.common.base.Preconditions.checkArgument;

import io.atomix.utils.memory.Memory;

/**
 * Direct {@link java.nio.ByteBuffer} based buffer.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DirectBuffer extends ByteBufferBuffer {

  protected DirectBuffer(
      final DirectBytes bytes, final int offset, final int initialCapacity, final int maxCapacity) {
    super(bytes, offset, initialCapacity, maxCapacity, null);
  }

  /**
   * Allocates a direct buffer with an initial capacity of {@code 4096} and a maximum capacity of
   * {@link Long#MAX_VALUE}.
   *
   * @return The direct buffer.
   * @see DirectBuffer#allocate(int)
   * @see DirectBuffer#allocate(int, int)
   */
  public static DirectBuffer allocate() {
    return allocate(DEFAULT_INITIAL_CAPACITY, MAX_SIZE);
  }

  /**
   * Allocates a direct buffer with the given initial capacity.
   *
   * @param initialCapacity The initial capacity of the buffer to allocate (in bytes).
   * @return The direct buffer.
   * @throws IllegalArgumentException If {@code capacity} is greater than the maximum allowed count
   *     for a {@link java.nio.ByteBuffer} - {@code Integer.MAX_VALUE - 5}
   * @see DirectBuffer#allocate()
   * @see DirectBuffer#allocate(int, int)
   */
  public static DirectBuffer allocate(final int initialCapacity) {
    return allocate(initialCapacity, MAX_SIZE);
  }

  /**
   * Allocates a new direct buffer.
   *
   * @param initialCapacity The initial capacity of the buffer to allocate (in bytes).
   * @param maxCapacity The maximum capacity of the buffer.
   * @return The direct buffer.
   * @throws IllegalArgumentException If {@code capacity} or {@code maxCapacity} is greater than the
   *     maximum allowed count for a {@link java.nio.ByteBuffer} - {@code Integer.MAX_VALUE - 5}
   * @see DirectBuffer#allocate()
   * @see DirectBuffer#allocate(int)
   */
  public static DirectBuffer allocate(final int initialCapacity, final int maxCapacity) {
    checkArgument(
        initialCapacity <= maxCapacity, "initial capacity cannot be greater than maximum capacity");
    return new DirectBuffer(
        DirectBytes.allocate((int) Math.min(Memory.Util.toPow2(initialCapacity), MAX_SIZE)),
        0,
        initialCapacity,
        maxCapacity);
  }

  @Override
  public DirectBuffer duplicate() {
    return new DirectBuffer((DirectBytes) bytes, offset(), capacity(), maxCapacity());
  }
}
