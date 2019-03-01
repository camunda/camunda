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
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BinaryValue extends BaseValue {
  protected final MutableDirectBuffer data = new UnsafeBuffer(0, 0);
  protected int length = 0;

  public BinaryValue() {}

  public BinaryValue(DirectBuffer initialValue, int offset, int length) {
    wrap(initialValue, offset, length);
  }

  @Override
  public void reset() {
    data.wrap(0, 0);
    length = 0;
  }

  public void wrap(DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  public void wrap(DirectBuffer buff, int offset, int length) {
    if (length == 0) {
      this.data.wrap(0, 0);
    } else {
      this.data.wrap(buff, offset, length);
    }
    this.length = length;
  }

  public void wrap(StringValue decodedKey) {
    this.wrap(decodedKey.getValue());
  }

  public DirectBuffer getValue() {
    return data;
  }

  @Override
  public void writeJSON(StringBuilder builder) {
    final byte[] bytes = new byte[length];
    data.getBytes(0, bytes);

    builder.append("\"");
    builder.append(new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8));
    builder.append("\"");
  }

  @Override
  public void write(MsgPackWriter writer) {
    writer.writeBinary(data);
  }

  @Override
  public void read(MsgPackReader reader) {
    final DirectBuffer buffer = reader.getBuffer();
    final int stringLength = reader.readBinaryLength();
    final int offset = reader.getOffset();

    reader.skipBytes(stringLength);

    this.wrap(buffer, offset, stringLength);
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedBinaryValueLength(length);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BinaryValue)) {
      return false;
    }

    final BinaryValue that = (BinaryValue) o;
    return length == that.length && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, length);
  }
}
