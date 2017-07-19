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
package io.zeebe.hashindex;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.hashindex.types.ByteArrayKeyHandler;
import io.zeebe.hashindex.types.LongValueHandler;
import org.agrona.DirectBuffer;

public class Bytes2LongHashIndex extends HashIndex<ByteArrayKeyHandler, LongValueHandler>
{
    public Bytes2LongHashIndex(int maxKeyLength)
    {
        super(maxKeyLength, SIZE_OF_LONG);
    }

    public Bytes2LongHashIndex(
            final int indexSize,
            final int blockCount,
            final int maxKeyLength)
    {
        super(indexSize, blockCount, maxKeyLength, SIZE_OF_LONG);
    }

    public long get(byte[] key, long missingValue)
    {
        keyHandler.setKey(key);
        valueHandler.theValue = missingValue;
        get();
        return valueHandler.theValue;
    }

    public long get(DirectBuffer buffer, int offset, int length, long missingValue)
    {
        keyHandler.setKey(buffer, offset, length);
        valueHandler.theValue = missingValue;
        get();
        return valueHandler.theValue;
    }

    public boolean put(byte[] key, long value)
    {
        keyHandler.setKey(key);
        valueHandler.theValue = value;
        return put();
    }

    public boolean put(DirectBuffer buffer, int offset, int length, long value)
    {
        keyHandler.setKey(buffer, offset, length);
        valueHandler.theValue = value;
        return put();
    }

    public long remove(byte[] key, long missingValue)
    {
        keyHandler.setKey(key);
        valueHandler.theValue = missingValue;
        remove();
        return valueHandler.theValue;
    }

    public long remove(DirectBuffer buffer, int offset, int length, long missingValue)
    {
        keyHandler.setKey(buffer, offset, length);
        valueHandler.theValue = missingValue;
        remove();
        return valueHandler.theValue;
    }

}
