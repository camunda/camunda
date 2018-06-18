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
package io.zeebe.map.types;

import io.zeebe.map.ValueHandler;
import org.agrona.DirectBuffer;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.UnsafeBuffer;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ByteArrayValueHandler implements ValueHandler {
  private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

  protected int valueLength;
  public UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

  @Override
  public int getValueLength() {
    return valueLength;
  }

  @Override
  public void setValueLength(int length) {
    this.valueLength = length;
  }

  public void setValue(byte[] value) {
    checkValueLength(value.length);
    valueBuffer.wrap(value);
  }

  public void setValue(DirectBuffer buffer, int offset, int length) {
    checkValueLength(length);
    valueBuffer.wrap(buffer, offset, length);
  }

  public DirectBuffer getValue() {
    return valueBuffer;
  }

  @Override
  public void writeValue(long writeValueAddr) {
    final int actualValueLength = valueBuffer.capacity();
    UNSAFE.copyMemory(
        valueBuffer.byteArray(),
        valueBuffer.addressOffset(),
        null,
        writeValueAddr,
        actualValueLength);
    UNSAFE.setMemory(writeValueAddr + actualValueLength, valueLength - actualValueLength, (byte) 0);
  }

  @Override
  public void readValue(long valueAddr, int valueLength) {
    valueBuffer.wrap(valueAddr, valueLength);
  }

  protected void checkValueLength(final int providedLength) {
    if (providedLength > valueLength) {
      throw new IllegalArgumentException(
          "Illegal byte array length: expected at most " + valueLength + ", got " + providedLength);
    }
  }
}
