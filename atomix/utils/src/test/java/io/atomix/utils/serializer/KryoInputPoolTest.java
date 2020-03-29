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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.esotericsoftware.kryo.io.Input;
import org.junit.Before;
import org.junit.Test;

public class KryoInputPoolTest {

  private KryoInputPool kryoInputPool;

  @Before
  public void setUp() throws Exception {
    kryoInputPool = new KryoInputPool();
  }

  @Test
  public void discardOutput() {
    final Input[] result = new Input[2];
    kryoInputPool.run(
        input -> {
          result[0] = input;
          return null;
        },
        KryoInputPool.MAX_POOLED_BUFFER_SIZE + 1);
    kryoInputPool.run(
        input -> {
          result[1] = input;
          return null;
        },
        0);
    assertTrue(result[0] != result[1]);
  }

  @Test
  public void recycleOutput() {
    final Input[] result = new Input[2];
    kryoInputPool.run(
        input -> {
          assertEquals(0, input.position());
          final byte[] payload = new byte[] {1, 2, 3, 4};
          input.setBuffer(payload);
          assertArrayEquals(payload, input.readBytes(4));
          result[0] = input;
          return null;
        },
        0);
    assertNull(result[0].getInputStream());
    assertEquals(0, result[0].position());
    kryoInputPool.run(
        input -> {
          result[1] = input;
          return null;
        },
        0);
    assertTrue(result[0] == result[1]);
  }
}
