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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferSerializer extends Serializer<ByteBuffer> {

  @Override
  public void write(final Kryo kryo, final Output output, final ByteBuffer object) {
    output.writeBoolean(object.isDirect());
    output.writeBoolean(ByteOrder.LITTLE_ENDIAN.equals(object.order()));
    output.writeInt(object.remaining());
    for (int i = object.position(); i < object.limit(); i++) {
      output.writeByte(object.get(i));
    }
  }

  @Override
  public ByteBuffer read(final Kryo kryo, final Input input, final Class<ByteBuffer> type) {
    final boolean isDirect = input.readBoolean();
    final boolean isLittleEndian = input.readBoolean();
    final int capacity = input.readInt();
    final ByteBuffer buffer;

    if (isDirect) {
      buffer = ByteBuffer.allocateDirect(capacity);
    } else {
      buffer = ByteBuffer.allocate(capacity);
    }

    if (isLittleEndian) {
      buffer.order(ByteOrder.LITTLE_ENDIAN);
    } else {
      buffer.order(ByteOrder.BIG_ENDIAN);
    }

    for (int i = 0; i < capacity; i++) {
      buffer.put(i, input.readByte());
    }

    return buffer;
  }
}
