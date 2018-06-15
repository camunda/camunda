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
package io.zeebe.map.iterator;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.map.types.ByteArrayKeyHandler;
import io.zeebe.map.types.ByteArrayValueHandler;

public class Bytes2BytesZbMapEntry implements ZbMapEntry<ByteArrayKeyHandler, ByteArrayValueHandler>
{
    private UnsafeBuffer key = new UnsafeBuffer(0, 0);
    private UnsafeBuffer value = new UnsafeBuffer(0, 0);

    @Override
    public void read(ByteArrayKeyHandler keyHandler, ByteArrayValueHandler valueHandler)
    {
        key.wrap(keyHandler.keyBuffer);
        value.wrap(valueHandler.valueBuffer);
    }

    public DirectBuffer getKey()
    {
        return key;
    }

    public DirectBuffer getValue()
    {
        return value;
    }

}
