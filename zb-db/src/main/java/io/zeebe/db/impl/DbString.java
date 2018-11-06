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
package io.zeebe.db.impl;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DbString implements DbKey, DbValue {

  private final DirectBuffer bytes = new UnsafeBuffer(0, 0);

  public void wrapString(String string) {
    this.bytes.wrap(string.getBytes());
  }

  public void wrapBuffer(DirectBuffer buffer) {
    bytes.wrap(buffer);
  }

  @Override
  public void wrap(DirectBuffer directBuffer, int offset, int length) {
    final int stringLen = directBuffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    final byte[] b = new byte[stringLen];
    directBuffer.getBytes(offset, b);
    bytes.wrap(b);
  }

  @Override
  public int getLength() {
    return Integer.BYTES // length of the string
        + bytes.capacity();
  }

  @Override
  public void write(MutableDirectBuffer mutableDirectBuffer, int offset) {
    final int length = bytes.capacity();
    mutableDirectBuffer.putInt(offset, length, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    mutableDirectBuffer.putBytes(offset, bytes, 0, bytes.capacity());
  }

  @Override
  public String toString() {
    return BufferUtil.bufferAsString(bytes);
  }

  public DirectBuffer getBuffer() {
    return bytes;
  }
}
