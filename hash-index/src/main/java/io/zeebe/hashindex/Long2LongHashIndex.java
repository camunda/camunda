package io.zeebe.hashindex;

import static io.zeebe.hashindex.HashIndexDescriptor.BLOCK_DATA_OFFSET;
import static io.zeebe.hashindex.HashIndexDescriptor.RECORD_KEY_OFFSET;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.hashindex.types.LongKeyHandler;
import io.zeebe.hashindex.types.LongValueHandler;
import org.agrona.BitUtil;

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
        return BLOCK_DATA_OFFSET + (recordsPerBlock * (RECORD_KEY_OFFSET + BitUtil.SIZE_OF_LONG + BitUtil.SIZE_OF_LONG));
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
