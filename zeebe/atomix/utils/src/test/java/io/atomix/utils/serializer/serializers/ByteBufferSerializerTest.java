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

import static org.assertj.core.api.Assertions.assertThat;

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
  private ByteBuffer buffer;

  @BeforeClass
  public static void register() {
    KRYO.register(ByteBuffer.class, new ByteBufferSerializer());
    KRYO.register(ByteBuffer.allocate(1).getClass(), new ByteBufferSerializer());
    KRYO.register(ByteBuffer.allocateDirect(1).getClass(), new ByteBufferSerializer());
    KRYO.addDefaultSerializer(ByteBuffer.class, new ByteBufferSerializer());
  }

  @Before
  public void setUp() {
    buffer = ByteBuffer.allocate(CAPACITY);

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
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.remaining()).isEqualTo(capacity);
    assertThat(deserialized.capacity()).isEqualTo(capacity);
    assertThat(deserialized.getLong(0)).isEqualTo(value);
  }

  @Test
  public void shouldSerializeDirectBuffer() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original = ByteBuffer.allocateDirect(capacity).putLong(0, value);

    // when
    KRYO.writeObject(output, original);
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.remaining()).isEqualTo(capacity);
    assertThat(deserialized.capacity()).isEqualTo(capacity);
    assertThat(deserialized.isDirect()).isTrue();
    assertThat(deserialized.getLong(0)).isEqualTo(value);
  }

  @Test
  public void shouldSerializeHeapBuffer() {
    // given
    final int value = 1;
    final int capacity = Long.BYTES;
    final ByteBuffer original = ByteBuffer.allocate(capacity).putLong(0, value);

    // when
    KRYO.writeObject(output, original);
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.remaining()).isEqualTo(capacity);
    assertThat(deserialized.capacity()).isEqualTo(capacity);
    assertThat(deserialized.isDirect()).isFalse();
    assertThat(deserialized.getLong(0)).isEqualTo(value);
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
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.order()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
    assertThat(deserialized.getLong(0)).isEqualTo(value);
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
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.order()).isEqualTo(ByteOrder.BIG_ENDIAN);
    assertThat(deserialized.getLong(0)).isEqualTo(value);
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
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.order()).isEqualTo(ByteOrder.BIG_ENDIAN);
    assertThat(deserialized.getInt(0)).isEqualTo(secondValue);
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
    buffer.flip();
    final ByteBuffer deserialized = KRYO.readObject(input, ByteBuffer.class);

    // then
    assertThat(deserialized.order()).isEqualTo(ByteOrder.BIG_ENDIAN);
    assertThat(deserialized.capacity()).isEqualTo(0);
  }
}
