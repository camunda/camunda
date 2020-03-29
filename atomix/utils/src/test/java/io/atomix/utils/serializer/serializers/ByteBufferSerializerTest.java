/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.serializer.serializers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/** Sets up a Kryo instance where we write/read from a shared buffer. */
public class ByteBufferSerializerTest {
  private static final int CAPACITY = 1024;
  private static final Kryo KRYO = new Kryo();

  private Output output;
  private Input input;

  @BeforeClass
  public static void register() {
    KRYO.register(ByteBuffer.class, new ByteBufferSerializer());
    KRYO.addDefaultSerializer(ByteBuffer.class, new ByteBufferSerializer());
  }

  @Before
  public void setUp() {
    final ByteBuffer buffer = ByteBuffer.allocate(CAPACITY);

    output = new ByteBufferOutput(buffer);
    input = new ByteBufferInput(buffer);
  }

  @After
  public void tearDown() {
    output.close();
    input.close();
  }

  @Test
  public void shouldSerializeRemainingOnly() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original = ByteBuffer.allocate(capacity * 2).putLong(value * 2).putLong(value);

    // when
    original.position(capacity);
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(capacity, deserialized.remaining());
    assertEquals(capacity, deserialized.capacity());
    assertEquals(value, deserialized.getLong(0));
  }

  @Test
  public void shouldSerializeDirectBuffer() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original = ByteBuffer.allocateDirect(capacity).putLong(0, value);

    // when
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(capacity, deserialized.remaining());
    assertEquals(capacity, deserialized.capacity());
    assertTrue(deserialized.isDirect());
    assertEquals(value, deserialized.getLong(0));
  }

  @Test
  public void shouldSerializeHeapBuffer() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original = ByteBuffer.allocate(capacity).putLong(0, value);

    // when
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(capacity, deserialized.remaining());
    assertEquals(capacity, deserialized.capacity());
    assertFalse(deserialized.isDirect());
    assertEquals(value, deserialized.getLong(0));
  }

  @Test
  public void shouldSerializeLittleEndianBuffer() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original =
        ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN).putLong(0, value);

    // when
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(ByteOrder.LITTLE_ENDIAN, deserialized.order());
    assertEquals(value, deserialized.getLong(0));
  }

  @Test
  public void shouldSerializeBigEndianBuffer() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original =
        ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN).putLong(0, value);

    // when
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(ByteOrder.BIG_ENDIAN, deserialized.order());
    assertEquals(value, deserialized.getLong(0));
  }

  @Test
  public void shouldSerializeBufferWithNonZeroPositionAndLimit() {
    // given
    final int capacity = Integer.BYTES * 4;
    final int firstPosition = Integer.BYTES;
    final int firstValue = 1;
    final int secondPosition = Integer.BYTES;
    final int secondValue = 2;
    final ByteBuffer original =
        ByteBuffer.allocate(capacity)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(firstPosition, firstValue)
            .putInt(secondPosition, secondValue);

    // when
    original.position(secondPosition).limit(secondPosition + Integer.BYTES);
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(ByteOrder.BIG_ENDIAN, deserialized.order());
    assertEquals(secondValue, deserialized.getInt(0));
  }

  @Test
  public void shouldSerializeZeroLengthBuffers() {
    // given
    final int capacity = Integer.BYTES;
    final int value = 1;
    final ByteBuffer original =
        ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN).putInt(0, value);

    // when
    original.position(capacity);
    KRYO.writeObject(output, original);
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertEquals(ByteOrder.BIG_ENDIAN, deserialized.order());
    assertEquals(0, deserialized.capacity());
  }
}
