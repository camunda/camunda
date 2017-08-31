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
package io.zeebe.map.types;

import org.agrona.DirectBuffer;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.map.ValueHandler;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ByteArrayValueHandler implements ValueHandler
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    public UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getValueLength()
    {
        return valueBuffer.capacity();
    }

    public void setValue(byte[] value)
    {
        valueBuffer.wrap(value);
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        valueBuffer.wrap(buffer, offset, length);
    }

    @Override
    public void writeValue(long writeValueAddr)
    {
        UNSAFE.copyMemory(valueBuffer.byteArray(), valueBuffer.addressOffset(), null, writeValueAddr, valueBuffer.capacity());
    }

    @Override
    public void readValue(long valueAddr, int valueLength)
    {
        UNSAFE.copyMemory(null, valueAddr, valueBuffer.byteArray(), valueBuffer.addressOffset(), valueLength);
    }
}
