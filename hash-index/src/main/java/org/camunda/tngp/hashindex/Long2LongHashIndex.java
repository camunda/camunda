package org.camunda.tngp.hashindex;

import org.camunda.tngp.hashindex.types.LongKeyHandler;
import org.camunda.tngp.hashindex.types.LongValueHandler;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import static uk.co.real_logic.agrona.BitUtil.*;

public class Long2LongHashIndex extends HashIndex<LongKeyHandler, LongValueHandler>
{
    public Long2LongHashIndex(
            MutableDirectBuffer indexBuffer,
            MutableDirectBuffer dataBuffer,
            int indexSize,
            int blockLength)
    {
        super(LongKeyHandler.class, LongValueHandler.class, indexBuffer, dataBuffer, indexSize, blockLength, SIZE_OF_LONG, SIZE_OF_LONG);
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

}
