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
package io.zeebe.hashindex.types;

import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import io.zeebe.hashindex.IndexValueHandler;
import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ByteArrayValueHandler implements IndexValueHandler
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    public byte[] theValue;

    @Override
    public int getValueLength()
    {
        return theValue.length;
    }

    @Override
    public void writeValue(long writeValueAddr)
    {
        UNSAFE.copyMemory(theValue, ARRAY_BASE_OFFSET, null, writeValueAddr, getValueLength());
    }

    @Override
    public void readValue(long valueAddr, int valueLength)
    {
        UNSAFE.copyMemory(null, valueAddr, theValue, ARRAY_BASE_OFFSET, valueLength);
    }
}
