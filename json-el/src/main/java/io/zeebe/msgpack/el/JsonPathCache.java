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
package io.zeebe.msgpack.el;

import java.util.Arrays;

import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class JsonPathCache
{
    private static final int INITIAL_CAPACITY = 12;

    private int size = 0;
    private int capacity = INITIAL_CAPACITY;

    private DirectBuffer[] keys = new DirectBuffer[INITIAL_CAPACITY];
    private MsgPackToken[] values = new MsgPackToken[INITIAL_CAPACITY];

    public MsgPackToken get(DirectBuffer key)
    {
        for (int k = 0; k < size; k++)
        {
            if (BufferUtil.equals(key, keys[k]))
            {
                return values[k];
            }
        }
        return null;
    }

    public void put(DirectBuffer key, MsgPackToken value)
    {
        if (size > capacity)
        {
            capacity = capacity * 2;

            keys = Arrays.copyOf(keys, capacity);
            values = Arrays.copyOf(values, capacity);
        }

        keys[size] = key;
        values[size] = value;

        size += 1;
    }

    public int size()
    {
        return size;
    }

    public void clear()
    {
        size = 0;
    }

    public void reset()
    {
        clear();

        capacity = INITIAL_CAPACITY;
        keys = new DirectBuffer[INITIAL_CAPACITY];
        values = new MsgPackToken[INITIAL_CAPACITY];
    }
}
