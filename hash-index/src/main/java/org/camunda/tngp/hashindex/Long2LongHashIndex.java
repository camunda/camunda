package org.camunda.tngp.hashindex;

import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.hashindex.types.LongKeyHandler;
import org.camunda.tngp.hashindex.types.LongValueHandler;

import static uk.co.real_logic.agrona.BitUtil.*;

public class Long2LongHashIndex extends HashIndex<LongKeyHandler, LongValueHandler>
{
    public Long2LongHashIndex(
            IndexStore indexStore,
            int indexSize,
            int blockLength)
    {
        super(indexStore, LongKeyHandler.class, LongValueHandler.class, indexSize, blockLength, SIZE_OF_LONG, SIZE_OF_LONG);
    }

    public Long2LongHashIndex(IndexStore indexStore)
    {
        super(indexStore, LongKeyHandler.class, LongValueHandler.class);
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
