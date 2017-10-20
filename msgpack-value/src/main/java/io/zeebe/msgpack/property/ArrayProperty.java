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

import java.util.Iterator;

import io.zeebe.msgpack.value.*;

public class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>> implements ValueArray<T>
{
    public ArrayProperty(String keyString, ArrayValue<T> value, T innerValue)
    {
        super(keyString, value);
        value.setInnerValue(innerValue);
    }

    public ArrayProperty(String key, ArrayValue<T> value, ArrayValue<T> defaultValue, T innerValue)
    {
        super(key, value, defaultValue);
        value.setInnerValue(innerValue);
        defaultValue.setInnerValue(innerValue);
    }

    @Override
    public Iterator<T> iterator()
    {
        return resolveValue().iterator();
    }

    @Override
    public T add()
    {
        if (!isSet)
        {
            isSet = true;

            if (defaultValue != null)
            {
                value.wrapArrayValue(defaultValue);
            }
        }

        return value.add();
    }

}
