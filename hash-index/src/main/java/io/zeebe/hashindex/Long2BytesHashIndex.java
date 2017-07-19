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

import io.zeebe.hashindex.types.ByteArrayValueHandler;
import io.zeebe.hashindex.types.LongKeyHandler;

/**
 * {@link HashIndex} that maps Long keys to Byte Array values. All values have a
 * max size which is defined on creation.
 */
public class Long2BytesHashIndex extends HashIndex<LongKeyHandler, ByteArrayValueHandler>
{
    private final int valueMaxLength;

    public Long2BytesHashIndex(int valueMaxLength)
    {
        super(SIZE_OF_LONG, valueMaxLength);
        this.valueMaxLength = valueMaxLength;
    }

    public Long2BytesHashIndex(int indexSize, int recordsPerBlock, int valueMaxLength)
    {
        super(indexSize, recordsPerBlock, SIZE_OF_LONG, valueMaxLength);
        this.valueMaxLength = valueMaxLength;
    }

    public boolean get(long key, byte[] value)
    {
        keyHandler.theKey = key;
        valueHandler.theValue = value;
        return get();
    }

    public boolean put(long key, byte[] value)
    {
        ensureValueMaxLength(value);

        keyHandler.theKey = key;
        valueHandler.theValue = value;
        return put();
    }

    public boolean remove(long key, byte[] value)
    {
        keyHandler.theKey = key;
        valueHandler.theValue = value;
        return remove();
    }

    private void ensureValueMaxLength(byte[] value)
    {
        if (value.length > valueMaxLength)
        {
            throw new IllegalArgumentException(String.format("Value exceeds max value length. Max value length is %d, got %d", valueMaxLength, value.length));
        }
    }
}
