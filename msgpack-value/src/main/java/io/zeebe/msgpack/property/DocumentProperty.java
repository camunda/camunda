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

import static io.zeebe.msgpack.value.DocumentValue.EMPTY_DOCUMENT;

import io.zeebe.msgpack.MsgpackPropertyException;
import io.zeebe.msgpack.value.DocumentValue;
import org.agrona.DirectBuffer;

public class DocumentProperty extends BaseProperty<DocumentValue> {
  public DocumentProperty(String keyString) {
    super(
        keyString,
        new DocumentValue(),
        new DocumentValue(EMPTY_DOCUMENT, 0, EMPTY_DOCUMENT.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(DirectBuffer data) {
    setValue(data, 0, data.capacity());
  }

  public void setValue(DirectBuffer data, int offset, int length) {
    try {
      this.value.wrap(data, offset, length);
      this.isSet = true;
    } catch (Exception e) {
      throw new MsgpackPropertyException(key, e);
    }
  }
}
