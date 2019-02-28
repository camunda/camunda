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

public class EnumValue<E extends Enum<E>> extends BaseValue {
  private final StringValue decodedValue = new StringValue();

  private final StringValue[] binaryEnumValues;
  private final E[] enumConstants;

  private E value;

  public EnumValue(Class<E> e, E defaultValue) {
    enumConstants = e.getEnumConstants();
    binaryEnumValues = new StringValue[enumConstants.length];

    for (int i = 0; i < enumConstants.length; i++) {
      final E constant = enumConstants[i];
      binaryEnumValues[i] = new StringValue(constant.toString());
    }

    this.value = defaultValue;
  }

  public EnumValue(Class<E> e) {
    this(e, null);
  }

  public E getValue() {
    return value;
  }

  public void setValue(E val) {
    this.value = val;
  }

  @Override
  public void reset() {
    value = null;
  }

  @Override
  public void writeJSON(StringBuilder builder) {
    binaryEnumValues[value.ordinal()].writeJSON(builder);
  }

  @Override
  public void write(MsgPackWriter writer) {
    binaryEnumValues[value.ordinal()].write(writer);
  }

  @Override
  public void read(MsgPackReader reader) {
    decodedValue.read(reader);

    for (int i = 0; i < binaryEnumValues.length; i++) {
      final StringValue val = binaryEnumValues[i];

      if (val.equals(decodedValue)) {
        value = enumConstants[i];
        return;
      }
    }

    throw new RuntimeException(String.format("Illegal enum value: %s.", decodedValue.toString()));
  }

  @Override
  public int getEncodedLength() {
    return binaryEnumValues[value.ordinal()].getEncodedLength();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EnumValue)) {
      return false;
    }

    final EnumValue<?> enumValue = (EnumValue<?>) o;
    return Objects.equals(getValue(), enumValue.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getValue());
  }
}
