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

/** {@link ByteBuffer} based direct bytes. */
public class DirectBytes extends ByteBufferBytes {

  protected DirectBytes(final ByteBuffer buffer) {
    super(buffer);
  }

  /**
   * Allocates a new direct byte array.
   *
   * @param size The count of the buffer to allocate (in bytes).
   * @return The direct buffer.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed count for
   *     an array on the Java heap - {@code Integer.MAX_VALUE - 5}
   */
  public static DirectBytes allocate(final int size) {
    if (size > MAX_SIZE) {
      throw new IllegalArgumentException(
          "size cannot for DirectBytes cannot be greater than " + MAX_SIZE);
    }
    return new DirectBytes(ByteBuffer.allocateDirect((int) size));
  }

  @Override
  protected ByteBuffer newByteBuffer(final int size) {
    return ByteBuffer.allocateDirect((int) size);
  }

  @Override
  public boolean isDirect() {
    return buffer.isDirect();
  }
}
