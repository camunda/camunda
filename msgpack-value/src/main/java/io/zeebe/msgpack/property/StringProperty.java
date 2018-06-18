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

import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.msgpack.value.StringValue;
import org.agrona.DirectBuffer;

public class StringProperty extends BaseProperty<StringValue> {

  public StringProperty(final String key) {
    super(key, new StringValue());
  }

  public StringProperty(final String key, final String defaultValue) {
    super(key, new StringValue(), new StringValue(defaultValue));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final String value) {
    this.value.wrap(getBytes(value));
    this.isSet = true;
  }

  public void setValue(final DirectBuffer buffer) {
    setValue(buffer, 0, buffer.capacity());
  }

  public void setValue(final DirectBuffer buffer, final int offset, final int length) {
    this.value.wrap(buffer, offset, length);
    this.isSet = true;
  }
}
