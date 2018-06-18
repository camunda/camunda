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
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.BinaryValue;
import org.agrona.DirectBuffer;

public class BinaryProperty extends BaseProperty<BinaryValue> {
  public BinaryProperty(String keyString) {
    super(keyString, new BinaryValue());
  }

  public BinaryProperty(String keyString, DirectBuffer defaultValue) {
    super(keyString, new BinaryValue(), new BinaryValue(defaultValue, 0, defaultValue.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(DirectBuffer data) {
    setValue(data, 0, data.capacity());
  }

  public void setValue(DirectBuffer data, int offset, int length) {
    this.value.wrap(data, offset, length);
    this.isSet = true;
  }
}
