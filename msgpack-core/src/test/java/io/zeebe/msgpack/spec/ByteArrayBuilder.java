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

public class ByteArrayBuilder {
  protected byte[] value = new byte[0];

  /**
   * NOTE: arguments are not converted to bytes (i.e. int becomes 4 byte) but the arguments are cast
   * to byte (i.e. lowest 8 bits are kept). This method exists solely for convenience to avoid
   * explicit casts to byte where this builder is used.
   */
  protected ByteArrayBuilder add(int... toAdd) {
    final byte[] arr = new byte[toAdd.length];
    for (int i = 0; i < toAdd.length; i++) {
      arr[i] = (byte) toAdd[i];
    }
    return add(arr);
  }

  protected ByteArrayBuilder add(byte... toAdd) {
    final byte[] newValue = new byte[value.length + toAdd.length];
    System.arraycopy(value, 0, newValue, 0, value.length);
    System.arraycopy(toAdd, 0, newValue, value.length, toAdd.length);
    value = newValue;
    return this;
  }
}
