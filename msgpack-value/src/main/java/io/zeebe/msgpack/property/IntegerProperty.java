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

import io.zeebe.msgpack.value.IntegerValue;

public class IntegerProperty extends BaseProperty<IntegerValue> {
  public IntegerProperty(String key) {
    super(key, new IntegerValue());
  }

  public IntegerProperty(String key, int defaultValue) {
    super(key, new IntegerValue(), new IntegerValue(defaultValue));
  }

  public int getValue() {
    return resolveValue().getValue();
  }

  public void setValue(int value) {
    this.value.setValue(value);
    this.isSet = true;
  }
}
