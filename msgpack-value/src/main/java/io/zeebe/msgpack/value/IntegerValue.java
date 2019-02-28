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
import java.util.Objects;

public class IntegerValue extends BaseValue {
  protected int value;

  public IntegerValue() {
    this(0);
  }

  public IntegerValue(int initialValue) {
    this.value = initialValue;
  }

  public void setValue(int val) {
    this.value = val;
  }

  public int getValue() {
    return value;
  }

  @Override
  public void reset() {
    value = 0;
  }

  @Override
  public void writeJSON(StringBuilder builder) {
    builder.append(value);
  }

  @Override
  public void write(MsgPackWriter writer) {
    writer.writeInteger(value);
  }

  @Override
  public void read(MsgPackReader reader) {
    final long longValue = reader.readInteger();

    if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
      throw new RuntimeException(
          String.format("Value doesn't fit into an integer: %s.", longValue));
    }

    value = (int) longValue;
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedLongValueLength(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof IntegerValue)) {
      return false;
    }

    final IntegerValue that = (IntegerValue) o;
    return getValue() == that.getValue();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getValue());
  }
}
