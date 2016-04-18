package org.camunda.tngp.hashindex;

import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.hashindex.types.ByteArrayKeyHandler;
import org.camunda.tngp.hashindex.types.LongValueHandler;

import static uk.co.real_logic.agrona.BitUtil.*;

public class Bytes2LongHashIndex extends HashIndex<ByteArrayKeyHandler, LongValueHandler>
{
    public Bytes2LongHashIndex(
            final IndexStore indexStore,
            final int indexSize,
            final int blockLength,
            final int keyLength)
    {
        super(indexStore, ByteArrayKeyHandler.class, LongValueHandler.class, indexSize, blockLength, keyLength, SIZE_OF_LONG);
    }

    public Bytes2LongHashIndex(IndexStore indexStore)
    {
        super(indexStore, ByteArrayKeyHandler.class, LongValueHandler.class);
    }

    public long get(byte[] key, long missingValue)
    {
        if(key.length < recordKeyLength())
        {
            throw new IllegalArgumentException("Illegal byte array length: expected "+recordKeyLength() + " got "+ key.length);
        }

        keyHandler.theKey = key;
        valueHandler.theValue = missingValue;
        get();
        return valueHandler.theValue;
    }

    public boolean put(byte[] key, long value)
    {
        if(key.length < recordKeyLength())
        {
            throw new IllegalArgumentException("Illegal byte array length: expected "+recordKeyLength() + " got "+ key.length);
        }

        keyHandler.theKey = key;
        valueHandler.theValue = value;
        return put();
    }

    public long remove(byte[] key, long missingValue)
    {
        if(key.length < recordKeyLength())
        {
            throw new IllegalArgumentException("Illegal byte array length: expected "+recordKeyLength() + " got "+ key.length);
        }

        keyHandler.theKey = key;
        valueHandler.theValue = missingValue;
        remove();
        return valueHandler.theValue;
    }

}
