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
package io.zeebe.msgpack.spec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackToken {
  public static final MsgPackToken NIL = new MsgPackToken();

  protected static final int MAX_MAP_ELEMENTS = 0x3fff_ffff;

  protected MsgPackType type = MsgPackType.NIL;
  protected int totalLength;

  // string
  protected UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

  // boolean
  protected boolean booleanValue;

  // map/array
  protected int size;

  // int
  protected long integerValue;

  // float32/float64
  protected double floatValue;

  public int getTotalLength() {
    return totalLength;
  }

  public void setTotalLength(int totalLength) {
    this.totalLength = totalLength;
  }

  public int getSize() {
    return size;
  }

  public MsgPackType getType() {
    return type;
  }

  public DirectBuffer getValueBuffer() {
    return valueBuffer;
  }

  public void setValue(DirectBuffer buffer, int offset, int length) {
    if (length == 0) {
      valueBuffer.wrap(0, 0);
    } else if (offset + length <= buffer.capacity()) {
      this.valueBuffer.wrap(buffer, offset, length);
    } else {
      final int result = offset + length;
      throw new MsgpackReaderException(
          String.format(
              "Reading %d bytes past buffer capacity(%d) in range [%d:%d]",
              result - buffer.capacity(), buffer.capacity(), offset, result));
    }
  }

  public void setValue(double value) {
    this.floatValue = value;
  }

  public void setValue(long value) {
    this.integerValue = value;
  }

  public void setValue(boolean value) {
    this.booleanValue = value;
  }

  public void setMapHeader(int size) {
    this.size = size;
  }

  public void setArrayHeader(int size) {
    this.size = size;
  }

  public void setType(MsgPackType type) {
    this.type = type;
  }

  public boolean getBooleanValue() {
    return booleanValue;
  }

  /**
   * when using this method, keep the value's format in mind; values of negative fixnum (signed) and
   * unsigned integer can return the same long value while representing different numbers
   */
  public long getIntegerValue() {
    return integerValue;
  }

  public double getFloatValue() {
    return floatValue;
  }
}
