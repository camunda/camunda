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

import io.zeebe.msgpack.MsgpackPropertyException;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.BaseValue;
import io.zeebe.msgpack.value.ValueArray;
import java.util.Iterator;

public class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>>
    implements ValueArray<T> {
  public ArrayProperty(String keyString, T innerValue) {
    super(keyString, new ArrayValue<>(innerValue));
    isSet = true;
  }

  @Override
  public void reset() {
    super.reset();
    isSet = true;
  }

  @Override
  public Iterator<T> iterator() {
    return resolveValue().iterator();
  }

  @Override
  public T add() {
    try {
      return value.add();
    } catch (Exception e) {
      throw new MsgpackPropertyException(getKey(), e);
    }
  }
}
