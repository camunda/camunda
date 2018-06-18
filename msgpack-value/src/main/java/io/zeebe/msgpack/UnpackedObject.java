/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.msgpack;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.msgpack.value.ObjectValue;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter {

  protected final MsgPackReader reader = new MsgPackReader();
  protected final MsgPackWriter writer = new MsgPackWriter();

  public void wrap(DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  @Override
  public void wrap(DirectBuffer buff, int offset, int length) {
    reader.wrap(buff, offset, length);
    try {
      read(reader);
    } catch (final Exception e) {
      throw new RuntimeException(
          "Could not deserialize object. Deserialization stuck at offset "
              + reader.getOffset()
              + " of length "
              + length,
          e);
    }
  }

  @Override
  public int getLength() {
    return getEncodedLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    writer.wrap(buffer, offset);
    write(writer);
  }
}
