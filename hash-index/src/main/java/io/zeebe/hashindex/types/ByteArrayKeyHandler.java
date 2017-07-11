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

import java.util.Arrays;

import io.zeebe.hashindex.IndexKeyHandler;
import org.agrona.DirectBuffer;
import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ByteArrayKeyHandler implements IndexKeyHandler
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    public byte[] theKey;
    public int keyLength;

    public void setKey(byte[] key)
    {
        checkKeyLength(key.length);
        System.arraycopy(key, 0, this.theKey, 0, key.length);
        zeroRemainingBytes(key.length);
    }

    public void setKey(DirectBuffer buffer, int offset, int length)
    {
        checkKeyLength(length);
        buffer.getBytes(offset, this.theKey, 0, length);
        zeroRemainingBytes(length);
    }

    @Override
    public void setKeyLength(int keyLength)
    {
        this.keyLength = keyLength;
        this.theKey = new byte[keyLength];
    }

    @Override
    public int getKeyLength()
    {
        return keyLength;
    }

    @Override
    public int keyHashCode()
    {
        int result = 1;

        for (int i = 0; i < keyLength; i++)
        {
            result = 31 * result + theKey[i];
        }

        return result;
    }

    @Override
    public void readKey(long keyAddr)
    {
        UNSAFE.copyMemory(null, keyAddr, theKey, ARRAY_BASE_OFFSET, keyLength);
    }

    @Override
    public void writeKey(long keyAddr)
    {
        UNSAFE.copyMemory(theKey, ARRAY_BASE_OFFSET, null, keyAddr, keyLength);
    }

    @Override
    public boolean keyEquals(long keyAddr)
    {
        final long thisOffset = ARRAY_BASE_OFFSET;
        final long thatOffset = keyAddr;

        for (int i = 0; i < keyLength; i++)
        {
            if (UNSAFE.getByte(theKey, thisOffset + i) != UNSAFE.getByte(null, thatOffset + i))
            {
                return false;
            }
        }

        return true;
    }

    protected void checkKeyLength(final int providedLength)
    {
        if (providedLength > keyLength)
        {
            throw new IllegalArgumentException("Illegal byte array length: expected at most " + keyLength + ", got " + providedLength);
        }
    }

    protected void zeroRemainingBytes(final int length)
    {
        if (length < keyLength)
        {
            Arrays.fill(theKey, length, keyLength, (byte) 0);
        }
    }

}
