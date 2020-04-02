/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.utils.serializer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BufferAwareByteArrayOutputStreamTest {

  @Test
  public void testBufferSize() throws Exception {
    final BufferAwareByteArrayOutputStream outputStream = new BufferAwareByteArrayOutputStream(8);
    assertEquals(8, outputStream.getBufferSize());
    outputStream.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    assertEquals(8, outputStream.getBufferSize());
    outputStream.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    assertEquals(16, outputStream.getBufferSize());
    outputStream.reset();
    assertEquals(16, outputStream.getBufferSize());
  }
}
