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

import io.atomix.utils.concurrent.ReferenceManager;

/** {@link java.nio.ByteBuffer} based buffer. */
public abstract class ByteBufferBuffer extends AbstractBuffer {
  protected final ByteBufferBytes bytes;

  public ByteBufferBuffer(
      final ByteBufferBytes bytes, final ReferenceManager<Buffer> referenceManager) {
    super(bytes, referenceManager);
    this.bytes = bytes;
  }

  public ByteBufferBuffer(
      final ByteBufferBytes bytes,
      final int offset,
      final int initialCapacity,
      final int maxCapacity,
      final ReferenceManager<Buffer> referenceManager) {
    super(bytes, offset, initialCapacity, maxCapacity, referenceManager);
    this.bytes = bytes;
  }

  @Override
  public byte[] array() {
    return bytes.array();
  }

  @Override
  protected void compact(final int from, final int to, final int length) {
    final byte[] bytes = new byte[1024];
    int position = from;
    while (position < from + length) {
      final int size = Math.min((from + length) - position, 1024);
      this.bytes.read(position, bytes, 0, size);
      this.bytes.write(0, bytes, 0, size);
      position += size;
    }
  }
}
