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
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import org.junit.Test;

/**
 * Direct buffer test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DirectBufferTest extends BufferTest {

  @Override
  protected Buffer createBuffer(final int capacity) {
    return DirectBuffer.allocate(capacity);
  }

  @Override
  protected Buffer createBuffer(final int capacity, final int maxCapacity) {
    return DirectBuffer.allocate(capacity, maxCapacity);
  }

  @Test
  public void testByteBufferToDirectBuffer() {
    final ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    byteBuffer.putLong(10);
    byteBuffer.flip();

    final DirectBuffer directBuffer = DirectBuffer.allocate(8);
    directBuffer.write(byteBuffer.array());
    directBuffer.flip();
    assertEquals(directBuffer.readLong(), byteBuffer.getLong());
    assertTrue(directBuffer.isDirect());
    directBuffer.release();
  }
}
