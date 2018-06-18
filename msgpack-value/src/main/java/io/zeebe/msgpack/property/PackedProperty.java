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

import io.zeebe.msgpack.value.PackedValue;
import org.agrona.DirectBuffer;

public class PackedProperty extends BaseProperty<PackedValue> {
  public PackedProperty(String key) {
    super(key, new PackedValue());
  }

  public PackedProperty(String key, DirectBuffer defaultValue) {
    super(key, new PackedValue(), new PackedValue(defaultValue, 0, defaultValue.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(DirectBuffer buffer, int offset, int length) {
    value.wrap(buffer, offset, length);
    this.isSet = true;
  }
}
