package org.camunda.tngp.hashindex.types;

import org.camunda.tngp.hashindex.IndexKeyHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class LongKeyHandler implements IndexKeyHandler
{
    public long theKey;

    @Override
    public void setKeyLength(int keyLength)
    {
        // ignore
    }

    @Override
    public int getKeyLength()
    {
        return BitUtil.SIZE_OF_LONG;
    }

    @Override
    public int keyHashCode()
    {
        final int hash = (int)theKey ^ (int)(theKey >>> 32);
        return hash ^ (hash >>> 16);
    }

    @Override
    public void readKey(MutableDirectBuffer buffer, int recordKeyOffset)
    {
        theKey = buffer.getLong(recordKeyOffset);
    }

    @Override
    public void writeKey(MutableDirectBuffer buffer, int recordKeyOffset)
    {
        buffer.putLong(recordKeyOffset, theKey);
    }

    @Override
    public boolean keyEquals(DirectBuffer buffer, int recordKeyOffset)
    {
        return theKey == buffer.getLong(recordKeyOffset);
    }
}
