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

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DbByte implements DbKey, DbValue {

  private byte value;

  public void wrapByte(byte value) {
    this.value = value;
  }

  @Override
  public void wrap(DirectBuffer directBuffer, int offset, int length) {
    value = directBuffer.getByte(offset);
  }

  @Override
  public int getLength() {
    return Byte.BYTES;
  }

  @Override
  public void write(MutableDirectBuffer mutableDirectBuffer, int offset) {
    mutableDirectBuffer.putByte(offset, value);
  }

  public byte getValue() {
    return value;
  }
}
