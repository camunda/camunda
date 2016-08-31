package org.camunda.tngp.hashindex;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.hashindex.types.ByteArrayKeyHandler;
import org.camunda.tngp.hashindex.types.LongValueHandler;

import org.agrona.DirectBuffer;

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
        checkKeyLength(key.length);

        keyHandler.setKey(key);
        valueHandler.theValue = missingValue;
        get();
        return valueHandler.theValue;
    }

    public long get(DirectBuffer buffer, int offset, int length, long missingValue)
    {
        checkKeyLength(length);

        keyHandler.setKey(buffer, offset, length);
        valueHandler.theValue = missingValue;
        get();
        return valueHandler.theValue;
    }

    public boolean put(byte[] key, long value)
    {
        checkKeyLength(key.length);

        keyHandler.setKey(key);
        valueHandler.theValue = value;
        return put();
    }

    public boolean put(DirectBuffer buffer, int offset, int length, long value)
    {
        checkKeyLength(length);

        keyHandler.setKey(buffer, offset, length);
        valueHandler.theValue = value;
        return put();
    }

    public long remove(byte[] key, long missingValue)
    {
        checkKeyLength(key.length);

        keyHandler.setKey(key);
        valueHandler.theValue = missingValue;
        remove();
        return valueHandler.theValue;
    }

    public long remove(DirectBuffer buffer, int offset, int length, long missingValue)
    {
        checkKeyLength(length);

        keyHandler.setKey(buffer, offset, length);
        valueHandler.theValue = missingValue;
        remove();
        return valueHandler.theValue;
    }

    protected void checkKeyLength(int providedKeyLength)
    {
        if (providedKeyLength > recordKeyLength())
        {
            throw new IllegalArgumentException("Illegal byte array length: expected at most " + recordKeyLength() + ", got " + providedKeyLength);
        }
    }
}
