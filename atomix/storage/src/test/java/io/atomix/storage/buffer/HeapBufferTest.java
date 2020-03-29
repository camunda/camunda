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

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import org.junit.Test;

/**
 * Heap buffer test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class HeapBufferTest extends BufferTest {

  @Override
  protected Buffer createBuffer(final int capacity) {
    return HeapBuffer.allocate(capacity);
  }

  @Override
  protected Buffer createBuffer(final int capacity, final int maxCapacity) {
    return HeapBuffer.allocate(capacity, maxCapacity);
  }

  @Test
  public void testByteBufferToHeapBuffer() {
    final ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    byteBuffer.putLong(10);
    byteBuffer.rewind();

    final HeapBuffer directBuffer = HeapBuffer.wrap(byteBuffer.array());
    assertEquals(directBuffer.readLong(), byteBuffer.getLong());
  }

  @Test
  public void testDirectToHeapBuffer() {
    final DirectBuffer directBuffer = DirectBuffer.allocate(8);
    directBuffer.writeLong(10);
    directBuffer.flip();

    final byte[] bytes = new byte[8];
    directBuffer.read(bytes);
    directBuffer.rewind();

    final HeapBuffer heapBuffer = HeapBuffer.wrap(bytes);
    assertEquals(directBuffer.readLong(), heapBuffer.readLong());

    directBuffer.release();
  }

  @Test
  public void testHeapToDirectBuffer() {
    final HeapBuffer heapBuffer = HeapBuffer.allocate(8);
    heapBuffer.writeLong(10);
    heapBuffer.flip();

    final DirectBuffer directBuffer = DirectBuffer.allocate(8);
    directBuffer.write(heapBuffer.array());
    directBuffer.flip();

    assertEquals(directBuffer.readLong(), heapBuffer.readLong());

    directBuffer.release();
  }
}
