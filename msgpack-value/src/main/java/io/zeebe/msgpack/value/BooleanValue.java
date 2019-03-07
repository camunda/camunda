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

public class BooleanValue extends BaseValue {
  protected boolean val = false;

  public BooleanValue() {
    this(false);
  }

  public BooleanValue(boolean initialValue) {
    this.val = initialValue;
  }

  @Override
  public void reset() {
    val = false;
  }

  public boolean getValue() {
    return val;
  }

  public void setValue(boolean value) {
    this.val = value;
  }

  @Override
  public void writeJSON(StringBuilder builder) {
    builder.append(val);
  }

  @Override
  public void write(MsgPackWriter writer) {
    writer.writeBoolean(val);
  }

  @Override
  public void read(MsgPackReader reader) {
    val = reader.readBoolean();
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedBooleanValueLength();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BooleanValue)) {
      return false;
    }

    final BooleanValue that = (BooleanValue) o;
    return val == that.val;
  }

  @Override
  public int hashCode() {
    return Objects.hash(val);
  }
}
