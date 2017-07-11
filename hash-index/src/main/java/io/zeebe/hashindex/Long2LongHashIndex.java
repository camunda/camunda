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

import static io.zeebe.hashindex.HashIndexDescriptor.BLOCK_DATA_OFFSET;
import static io.zeebe.hashindex.HashIndexDescriptor.getRecordLength;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.hashindex.types.LongKeyHandler;
import io.zeebe.hashindex.types.LongValueHandler;

public class Long2LongHashIndex extends HashIndex<LongKeyHandler, LongValueHandler>
{
    public Long2LongHashIndex(
            int indexSize,
            int recordsPerBlock)
    {
        super(LongKeyHandler.class, LongValueHandler.class, indexSize, maxBlockLength(recordsPerBlock), SIZE_OF_LONG);
    }

    private static int maxBlockLength(int recordsPerBlock)
    {
        return BLOCK_DATA_OFFSET + (recordsPerBlock * getRecordLength(SIZE_OF_LONG, SIZE_OF_LONG));
    }

    public long get(long key, long missingValue)
    {
        keyHandler.theKey = key;
        valueHandler.theValue = missingValue;
        get();
        return valueHandler.theValue;
    }

    public boolean put(long key, long value)
    {
        keyHandler.theKey = key;
        valueHandler.theValue = value;
        return put();
    }

    public long remove(long key, long missingValue)
    {
        keyHandler.theKey = key;
        valueHandler.theValue = missingValue;
        remove();
        return valueHandler.theValue;
    }

}
