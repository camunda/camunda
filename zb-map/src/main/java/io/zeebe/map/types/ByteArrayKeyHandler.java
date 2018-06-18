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

import io.zeebe.map.KeyHandler;
import org.agrona.DirectBuffer;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.UnsafeBuffer;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ByteArrayKeyHandler implements KeyHandler {
  private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

  public int keyLength;
  public UnsafeBuffer keyBuffer = new UnsafeBuffer(0, 0);

  public void setKey(byte[] key) {
    checkKeyLength(key.length);
    keyBuffer.wrap(key);
  }

  public void setKey(DirectBuffer buffer, int offset, int length) {
    checkKeyLength(length);
    keyBuffer.wrap(buffer, offset, length);
  }

  @Override
  public void setKeyLength(int keyLength) {
    this.keyLength = keyLength;
  }

  @Override
  public int getKeyLength() {
    return keyLength;
  }

  @Override
  public int keyHashCode() {
    int result = 1;

    for (int i = 0; i < keyBuffer.capacity(); i++) {
      result = 31 * result + keyBuffer.getByte(i);
    }

    if (keyBuffer.capacity() < keyLength) {
      for (int i = keyBuffer.capacity(); i < keyLength; i++) {
        result = 31 * result;
      }
    }
    return result;
  }

  @Override
  public void readKey(long keyAddr) {
    keyBuffer.wrap(keyAddr, keyLength);
  }

  @Override
  public void writeKey(long keyAddr) {
    final int actualValueLength = keyBuffer.capacity();
    UNSAFE.copyMemory(
        keyBuffer.byteArray(), keyBuffer.addressOffset(), null, keyAddr, actualValueLength);
    UNSAFE.setMemory(keyAddr + actualValueLength, keyLength - actualValueLength, (byte) 0);
  }

  @Override
  public boolean keyEquals(long keyAddr) {
    final long thatOffset = keyAddr;

    for (int i = 0; i < keyBuffer.capacity(); i++) {
      if (keyBuffer.getByte(i) != UNSAFE.getByte(null, thatOffset + i)) {
        return false;
      }
    }

    if (keyBuffer.capacity() < keyLength) {
      for (int i = keyBuffer.capacity(); i < keyLength; i++) {
        if (UNSAFE.getByte(null, thatOffset + i) != 0) {
          return false;
        }
      }
    }
    return true;
  }

  protected void checkKeyLength(final int providedLength) {
    if (providedLength > keyLength) {
      throw new IllegalArgumentException(
          "Illegal byte array length: expected at most " + keyLength + ", got " + providedLength);
    }
  }
}
