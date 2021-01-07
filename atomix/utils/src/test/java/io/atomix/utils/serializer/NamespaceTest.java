/*
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
package io.atomix.utils.serializer;

import static org.junit.Assert.assertEquals;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.nio.ByteBuffer;
import org.junit.Test;

public class NamespaceTest {

  @Test
  public void shouldDeserializeObject() {
    // given
    final Namespace ns = new Namespace.Builder().register(Integer.class).build();
    final Integer want = 99;

    // when
    final byte[] ser = ns.serialize(want);
    final Object got = ns.deserialize(ser);

    // then
    assertEquals(want, got);
  }

  @Test
  public void shouldDeserializeObjectWithBuffer() {
    // given
    final Namespace ns = new Namespace.Builder().register(Integer.class).build();
    final Integer want = 99;

    // when
    final ByteBuffer buffer = ByteBuffer.allocate(4);
    ns.serialize(want, buffer);
    buffer.flip();
    final Object got = ns.deserialize(buffer);

    // then
    assertEquals(want, got);
  }

  @Test
  public void shouldRegisterMultipleTypesSimultaneously() {
    // given
    final Namespace ns =
        new Namespace.Builder().register(new NumberSerializer(), Integer.class, Long.class).build();
    final Long expectedLong = 5L;
    final Integer expectedInteger = 7;

    // when
    final Long gotLong = ns.deserialize(ns.serialize(expectedLong));
    final Integer gotInteger = ns.deserialize(ns.serialize(expectedInteger));

    // then
    assertEquals(expectedLong, gotLong);
    assertEquals(expectedInteger, gotInteger);
  }

  private static class NumberSerializer extends Serializer<Number> {

    @Override
    public void write(final Kryo kryo, final Output output, final Number object) {
      if (Integer.class.equals(object.getClass())) {
        output.write(object.intValue());
      } else {
        output.writeLong(object.longValue());
      }
    }

    @Override
    public Number read(final Kryo kryo, final Input input, final Class<? extends Number> type) {
      if (Integer.class.equals(type)) {
        return input.read();
      } else {
        return input.readLong();
      }
    }
  }
}
